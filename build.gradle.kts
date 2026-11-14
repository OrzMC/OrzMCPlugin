import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

buildscript {
    repositories {
        // 官方Maven中心仓（仅用于解析 snakeyaml，不用于 Gradle 插件，
        // 插件通过 plugins {} 块中的默认仓库解析，避免阿里云镜像 502 阻断 CI）
        mavenCentral()
    }
    dependencies {
        classpath("org.yaml:snakeyaml:2.7")
    }
}

val pluginYaml = Yaml().load(File("src/main/resources/paper-plugin.yml").inputStream()) as Map<String, Any>
group = (pluginYaml["main"] as String).split('.').dropLast(1).joinToString(".")
version = pluginYaml["version"] as String
description = pluginYaml["description"] as String

// PaperMC 插件开发，项目配置文档: https://docs.papermc.io/paper/dev/project-setup
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("plugin_jdk_min_version") as String))
}
repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    mavenCentral()
}
dependencies {
    // orc-mc-api 子模块（纯 Java 端口与模型）
    implementation(project(":orzmc-api"))

    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version") as String}")
    // Log4J（Paper 运行时自带；compileOnly 不打进 jar）——$e 命令日志窗口收集 Appender
    compileOnly("org.apache.logging.log4j:log4j-api:2.26.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.26.1")
    testImplementation("org.apache.logging.log4j:log4j-core:2.26.1")
    // LuckPerms API（软依赖：LP 插件在运行时提供 API 类——compileOnly 不打进 jar，
    // shadowJar 排除 net/luckperms 避免类加载器冲突；paper-plugin.yml dependencies 新格式声明软依赖）
    compileOnly("net.luckperms:api:5.5")
    testImplementation("net.luckperms:api:5.5")
    // WebSocket client used by the EasyBot event stream.
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    // Minecraft World Backup Lib
    implementation("io.github.wangzhizhou:backup-core:0.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("io.papermc.paper:paper-api:${property("paper_api_version") as String}")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.24.0")
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
}
integrationTestSourceSet.compileClasspath += sourceSets.main.get().output
integrationTestSourceSet.runtimeClasspath += integrationTestSourceSet.output + integrationTestSourceSet.compileClasspath


configurations.getByName("integrationTestImplementation").extendsFrom(
    configurations.implementation.get(),
    configurations.testImplementation.get()
)
configurations.getByName("integrationTestRuntimeOnly").extendsFrom(
    configurations.runtimeOnly.get(),
    configurations.testRuntimeOnly.get()
)

dependencies {
    add("integrationTestImplementation", "org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.117.0")
}

// 项目编译时插件添加
plugins {
    kotlin("jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
    // 工程内直接调试服务端插件：https://docs.papermc.io/paper/dev/debugging#using-direct-debugging
    id("xyz.jpenilla.run-paper") version "3.1.0"
    // 自动发布版本配置文档：https://docs.papermc.io/misc/hangar-publishing/
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    // Modrinth 自动发布：https://github.com/modrinth/minotaur
    id("com.modrinth.minotaur") version "2.10.0"
    id("com.diffplug.spotless") version "8.11.0"
    // 测试重试：CI 机器负载抖动导致的偶发失败自动重跑（最多 2 次），避免「假红」阻断 PR。
    // 注意：重试是安全网不是修复手段——重跑才通过的用例仍须按 flaky 修复（见 docs/quality-testing-plan.md §4.2）
    id("org.gradle.test-retry") version "1.6.6"
    id("jacoco")
}

// 代码格式化
spotless {
    java {
        // 使用 Palantir 格式，指定新版本以兼容 JDK 25
        palantirJavaFormat("2.93.0")
    }
}

// 版本发布相关
fun executeGitCommand(vararg command: String): String {
    val byteOut = ByteArrayOutputStream()
    serviceOf<ExecOperations>().exec {
        commandLine = listOf("git", *command)
        standardOutput = byteOut
    }
    return byteOut.toString(Charsets.UTF_8.name()).trim()
}

fun latestCommitMessage(): String {
    // 发布 changelog 取最近提交标题（%s）单行：%B 整段 PR 描述过长/含特殊字符曾在 Hangar 上传触发
    // 400（2026-09-08 #411 里程碑）；单行标题简洁且对 Hangar 友好
    return runCatching { executeGitCommand("log", "-1", "--pretty=%s") }
        .getOrElse { "OrzMC ${project.version} build" }
}

/** 轮询日志文件直到命中 pattern，返回已等待毫秒数；超时返回 -1。 */
private fun waitForLog(logFile: File, pattern: Regex, timeoutMs: Long): Long {
    val start = System.nanoTime()
    while ((System.nanoTime() - start) < timeoutMs * 1_000_000L) {
        if (logFile.exists() && pattern.containsMatchIn(logFile.readText(Charsets.UTF_8))) {
            return (System.nanoTime() - start) / 1_000_000L
        }
        Thread.sleep(250)
    }
    return -1
}

/** 读取日志尾部若干行，用于失败诊断。 */
private fun logTail(logFile: File, lines: Int = 40): String {
    if (!logFile.exists()) {
        return "(smoke.log 不存在)"
    }
    return logFile.readText(Charsets.UTF_8).split("\n").takeLast(lines).joinToString("\n")
}

// =====================================================================
// 真实服务端冒烟 / 自举 E2E（foliaSmoke、e2eLocal* 共用实现）
// =====================================================================

/** 一条控制台断言：向服务端 stdin 发 command，要求此后「新增」日志出现 expect。 */
private data class ConsoleCheck(val label: String, val command: String, val expect: Regex, val timeoutMs: Long = 30_000L)

/** 白名单读写探针用的合法玩家名（固定值：写后立刻删，跑完不留脏数据，中断也不会累积）。 */
private val PROBE_PLAYER = "E2EProbe"

/** 每次运行唯一的 say 标记（避免命中历史日志）。 */
private fun uniqueE2eTag(): String = "e2e-" + UUID.randomUUID().toString().take(6)

/**
 * Bot 命令链路的控制台断言：经 `/orzdebug` 模拟群用户命令，覆盖「解析 → 业务 → 回执」全链路。
 *
 * <p>这些是 MockBukkit 测不到的部分：真实 Brigadier 命令注册、Folia 分页调度（BUG-E2E-001）、
 * 插件代执行控制台命令（$e）。</p>
 */
private fun botConsoleChecks(tag: String): List<ConsoleCheck> = listOf(
    // 读路径
    ConsoleCheck("Bot 帮助（\$h）", "orzdebug \$h", Regex("群指令帮助")),
    ConsoleCheck("在线列表（\$l）", "orzdebug \$l", Regex("当前在线")),
    ConsoleCheck("白名单分页（\$w，Folia 分页回归护栏）", "orzdebug \$w", Regex("当前白名单玩家")),
    ConsoleCheck("访问规则查询（\$d）", "orzdebug \$d", Regex("访问规则为空|黑名单")),
    // 写路径（白名单落盘 + 读回；第二条负责还原，跑完不留脏数据）
    ConsoleCheck(
        "白名单写入（\$a）",
        "orzdebug \$a $PROBE_PLAYER",
        Regex("白名单添加[\\s\\S]{0,200}$PROBE_PLAYER"),
    ),
    ConsoleCheck(
        "白名单删除（\$r，含还原）",
        "orzdebug \$r $PROBE_PLAYER",
        Regex("白名单移除[\\s\\S]{0,200}$PROBE_PLAYER"),
    ),
    // 跨层：插件代执行控制台命令 / 配置热加载 / 维护模式状态机
    ConsoleCheck("控制台命令执行（\$e）", "orzdebug \$e say $tag", Regex(Regex.escape(tag))),
    ConsoleCheck("配置热加载（/config reload）", "config reload", Regex("已重新加载")),
    ConsoleCheck("维护模式进入（/maintenance on）", "maintenance on", Regex("已进入手动维护模式")),
    ConsoleCheck("维护模式退出（/maintenance off）", "maintenance off", Regex("已退出维护模式")),
)

/**
 * LuckPerms 就绪后的附加断言（真实 LP + 真实服务端）：
 * ① `$p` 无参数 → 输出用法（LP 未就绪时这里会是「未检测到 LuckPerms」，所以这条能锁定 LP 集成成功）；
 * ② `$p u 未知玩家` → 「找不到玩家」（离线解析降级路径，不得抛异常/超时）；
 * ③ `/rank` 控制台执行 → 「需要玩家执行」（PlayerOnly 拦截器链）。
 */
private fun luckPermsChecks(): List<ConsoleCheck> = listOf(
    ConsoleCheck("权限命令可用（LP 已就绪，\$p 输出用法）", "orzdebug \$p", Regex("\\\$p 权限管理")),
    ConsoleCheck("离线玩家解析降级（\$p u 未知玩家）", "orzdebug \$p u E2EProbe", Regex("找不到玩家")),
    ConsoleCheck("玩家专属命令的控制台拒绝（/rank）", "rank", Regex("需要玩家执行")),
)

/** 日志「稳定」偏移：回退到最后一个换行之后，避免切在多字节字符中间产生乱码前缀。 */
private fun stableLogOffset(logFile: File): Long {
    val length = logFile.length()
    if (length == 0L) return 0L
    val tailSize = minOf(length, 8192L).toInt()
    val buf = ByteArray(tailSize)
    RandomAccessFile(logFile, "r").use { raf ->
        raf.seek(length - tailSize)
        raf.readFully(buf)
    }
    for (i in buf.indices.reversed()) {
        if (buf[i] == '\n'.code.toByte()) return length - tailSize + i + 1
    }
    return 0L
}

/** 只匹配 fromOffset 之后追加的日志：避免「早先日志已出现该文本」造成假绿。 */
private fun awaitLogAfter(logFile: File, fromOffset: Long, pattern: Regex, timeoutMs: Long): Boolean {
    val start = System.nanoTime()
    while ((System.nanoTime() - start) < timeoutMs * 1_000_000L) {
        val length = logFile.length()
        if (length > fromOffset) {
            val buf = ByteArray((length - fromOffset).toInt())
            RandomAccessFile(logFile, "r").use { raf ->
                raf.seek(fromOffset)
                raf.readFully(buf)
            }
            if (pattern.containsMatchIn(String(buf, Charsets.UTF_8))) return true
        }
        Thread.sleep(150)
    }
    return false
}

/**
 * 下载服务端 jar（Paper Fill API v3）。成功且 SHA256 通过才写标记文件；中途被杀留下的残缺 jar
 * 因标记缺失会被判 out-of-date 重下。返回 jar 路径；任务名 = `download<displayName>Jar`。
 */
private fun Project.registerServerJarDownload(
    version: String,
    fillProject: String,
    dirName: String,
    displayName: String,
): File {
    val jarFile = layout.buildDirectory.file("$dirName/$fillProject-$version.jar")
    val markerFile = layout.buildDirectory.file("$dirName/.$fillProject-$version.ok")
    tasks.register("download${displayName}Jar") {
        group = "verification"
        description = "下载 $displayName 服务端 jar（Paper Fill API v3，SnakeYAML 解析 JSON 响应）"
        inputs.property("serverVersion", version)
        outputs.file(jarFile)
        outputs.file(markerFile)
        doLast {
            val file = jarFile.get().asFile
            val apiUrl = "https://fill.papermc.io/v3/projects/$fillProject/versions/$version/builds/latest"
            // Fill API 返回 JSON，而 JSON ⊂ YAML，直接复用 buildscript 已有的 SnakeYAML 解析
            val response = URI(apiUrl).toURL().openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val data = Yaml().load(response) as Map<*, *>
            val serverDefault = (data["downloads"] as Map<*, *>)["server:default"] as Map<*, *>
            val downloadUrl = serverDefault["url"] as String
            val expectedSha = (serverDefault["checksums"] as Map<*, *>)["sha256"] as String
            file.parentFile.mkdirs()
            val conn = URI(downloadUrl).toURL().openConnection()
            conn.setRequestProperty("User-Agent", "OrzMC/$displayName-smoke")
            conn.connect()
            val digest = MessageDigest.getInstance("SHA-256")
            conn.getInputStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                file.delete()
                throw GradleException("$displayName jar SHA256 校验失败: expected=$expectedSha actual=$actualSha")
            }
            markerFile.get().asFile.writeText("sha256=$actualSha\n")
            logger.lifecycle("已下载 $displayName $version → ${file.absolutePath}")
        }
    }
    return jarFile.get().asFile
}

/**
 * 从 Modrinth 下载插件 jar（sha512 校验，缓存 + 标记文件）；返回 jar 路径，任务名由调用方指定。
 *
 * <p>用于 L1.5 引入真实依赖插件（如 LuckPerms）——历史上 LP 异步 + Folia region 线程出过死锁，
 * 而 MockBukkit 里没有 LP，这条链路此前完全不在 PR 覆盖内。</p>
 */
private fun Project.registerModrinthPluginDownload(
    taskName: String,
    projectSlug: String,
    mcVersion: String,
    loader: String,
    dirName: String,
    displayName: String,
): File {
    val jarFile = layout.buildDirectory.file("$dirName/$projectSlug-$mcVersion.jar")
    val markerFile = layout.buildDirectory.file("$dirName/.$projectSlug-$mcVersion.ok")
    tasks.register(taskName) {
        group = "verification"
        description = "下载 $displayName 插件 jar（Modrinth API，sha512 校验）"
        inputs.property("mcVersion", mcVersion)
        outputs.file(jarFile)
        outputs.file(markerFile)
        doLast {
            val file = jarFile.get().asFile
            val apiUrl = "https://api.modrinth.com/v2/project/$projectSlug/version" +
                "?loaders=%5B%22$loader%22%5D&game_versions=%5B%22$mcVersion%22%5D"
            val conn = URI(apiUrl).toURL().openConnection()
            conn.setRequestProperty("User-Agent", "OrzMC/plugin-ci")
            val response = conn.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val versions = Yaml().load(response) as List<Map<*, *>>
            val newest = versions.firstOrNull()
                ?: throw GradleException("Modrinth 没有 $projectSlug 在 $mcVersion/$loader 下的版本")
            val files = newest["files"] as List<Map<*, *>>
            val chosen = files.firstOrNull { it["primary"] == true } ?: files.first()
            val downloadUrl = chosen["url"] as String
            val expectedSha = (chosen["hashes"] as Map<*, *>)["sha512"].toString().lowercase()
            file.parentFile.mkdirs()
            val download = URI(downloadUrl).toURL().openConnection()
            download.setRequestProperty("User-Agent", "OrzMC/plugin-ci")
            download.connect()
            val digest = MessageDigest.getInstance("SHA-512")
            download.getInputStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                file.delete()
                throw GradleException("$displayName jar sha512 校验失败: expected=$expectedSha actual=$actualSha")
            }
            markerFile.get().asFile.writeText("sha512=$actualSha\n")
            logger.lifecycle("已下载 $displayName ${newest["version_number"]} → ${file.absolutePath}")
        }
    }
    return jarFile.get().asFile
}

/**
 * 起真实服务端并跑断言：隔离 run 目录 → 放入插件 → 启动 → 等 Done → 等插件加载 →
 * 逐条控制台断言（只匹配本次命令之后的日志）→ stop → 断言 exit 0。
 */
private fun Project.runHeadlessServerSmoke(
    core: String,
    serverJar: File,
    runDir: File,
    shadowJarFile: File,
    javaBin: String,
    port: Int,
    prepare: (File) -> Unit,
    preChecks: List<Pair<String, Regex>>,
    checks: List<ConsoleCheck>,
    extraPlugins: List<File> = emptyList(),
    extraChecks: List<ConsoleCheck> = emptyList(),
) {
    runDir.mkdirs()
    File(runDir, "eula.txt").writeText("eula=true\n")
    val pluginsDir = File(runDir, "plugins").apply { mkdirs() }
    shadowJarFile.copyTo(File(pluginsDir, "OrzMC.jar"), overwrite = true)
    // 真实依赖插件（如 LuckPerms）：原样放进 plugins/，文件名保持不变（插件靠它识别自己）
    for (plugin in extraPlugins) {
        plugin.copyTo(File(pluginsDir, plugin.name), overwrite = true)
    }
    val dataDir = File(pluginsDir, "OrzMC").apply { mkdirs() }
    // OrzMC 默认 deny-list 拦截 stop/reload 等危险命令（安全加固）；冒烟测试需要干净退出，
    // 故在隔离 run 目录放一份空 deny-list 配置（仅影响本次冒烟，不触碰真实配置）
    File(dataDir, "config.yml").writeText("guard:\n  blocked_commands: []\n")
    prepare(dataDir)

    val logFile = File(runDir, "smoke.log").apply { if (exists()) delete() }
    // 用 toolchain JDK 启动（Folia 26.2 最低要求 Java 25），不依赖 Gradle 守护进程 JDK
    val cmd = listOf(
        javaBin, "-Xmx2G", "-Ddisable.watchdog=true",
        "-jar", serverJar.absolutePath,
        "--nogui", "--nojline", "--online-mode=false", "--port", port.toString()
    )
    logger.lifecycle("启动 $core 冒烟服务器: ${cmd.joinToString(" ")}")
    val proc = ProcessBuilder(cmd)
        .directory(runDir)
        .redirectErrorStream(true)
        .redirectOutput(logFile)
        .start()

    fun abort(message: String): Nothing {
        proc.destroyForcibly()
        proc.waitFor(30, TimeUnit.SECONDS)
        throw GradleException("$message\n日志尾部:\n${logTail(logFile)}")
    }

    val startedMs = waitForLog(logFile, Regex("Done \\(.*\\)"), 240_000)
    if (startedMs < 0) abort("$core 服务器 240s 内未完成启动。")
    logger.lifecycle("$core 已启动（${startedMs}ms）。校验 OrzMC 加载...")
    if (waitForLog(logFile, Regex("OrzMC"), 15_000) < 0) abort("OrzMC 插件未加载。")

    for ((label, pattern) in preChecks) {
        logger.lifecycle("校验: $label")
        if (!awaitLogAfter(logFile, 0L, pattern, 30_000)) abort("未通过: $label（期望 ${pattern.pattern}）")
    }

    for (check in checks + extraChecks) {
        logger.lifecycle("控制台断言: ${check.label} → ${check.command}")
        val from = stableLogOffset(logFile)
        try {
            proc.outputStream.write((check.command + "\n").toByteArray(Charsets.UTF_8))
            proc.outputStream.flush()
        } catch (e: IOException) {
            abort("无法向服务器写入命令（进程可能已退出）: ${check.label}（${e.message}）")
        }
        if (!awaitLogAfter(logFile, from, check.expect, check.timeoutMs)) {
            abort("控制台断言失败: ${check.label}（命令: ${check.command}，期望: ${check.expect.pattern}）")
        }
    }

    logger.lifecycle("发送 stop...")
    proc.outputStream.write("stop\n".toByteArray(Charsets.UTF_8))
    proc.outputStream.flush()
    if (!proc.waitFor(90, TimeUnit.SECONDS)) {
        proc.destroyForcibly()
        throw GradleException("$core 在 stop 后 90s 内未退出。日志尾部:\n${logTail(logFile)}")
    }
    if (proc.exitValue() != 0) {
        throw GradleException("$core 退出码非零: ${proc.exitValue()}。日志尾部:\n${logTail(logFile)}")
    }
    logger.lifecycle("$core 冒烟通过：插件加载 + ${checks.size + extraChecks.size} 条控制台断言 + 干净退出（exit 0）")
}

val githubRunNumber: String? = System.getenv("GITHUB_RUN_NUMBER")
val githubRefType: String? = System.getenv("GITHUB_REF_TYPE")
val githubEventName: String? = System.getenv("GITHUB_EVENT_NAME")
val githubRef: String? = System.getenv("GITHUB_REF")
val versionString: String = version as String
val isPrBuild: Boolean = (githubEventName == "pull_request")

val tagName: String? = if (githubRefType == "tag") {
    githubRef?.removePrefix("refs/tags/")
} else null

// 纯 SemVer tag（不含 -）→ Release，其余 → Dev（未来可扩展 alpha/beta）
val isReleaseTag: Boolean = tagName != null && !tagName.contains("-")

// Extract PR number from GITHUB_REF (format: refs/pull/42/merge)
val prNumber: String? = if (isPrBuild && githubRef != null) {
    Regex("refs/pull/(\\d+)/merge").find(githubRef)?.groupValues?.get(1)
} else null

val shadowJarVersion: String = when {
    // Tag → Release: 直接使用 tag 名称（已经是纯 SemVer）
    isReleaseTag && tagName != null -> tagName
    // PR 构建 → {version}-pr.{PR}.{run}（去掉 #，改用 . 分隔，符合 SemVer）
    isPrBuild && prNumber != null -> "${versionString}-pr.${prNumber}.${githubRunNumber}"
    // CI 分支 push → {version}-dev.{run}
    githubRunNumber != null -> "${versionString}-dev.${githubRunNumber}"
    // 本地开发 → {version}-dev
    else -> "${versionString}-dev"
}

// Use the commit description for the changelog
val changelogContent: String = latestCommitMessage()

// ---- 运行时自更新版本信息烘焙（build-info）----
// 生成的 orzmc-build.properties 打进 jar，供插件自更新精确比对「当前运行版本」
// 与 Hangar 通道最新版本。构建时间取 HEAD 提交时间（稳定：同一提交反复构建不产生
// 增量重编译，Gradle 依 inputs 判定 up-to-date 跳过）。
val buildCommitTime: String = runCatching { executeGitCommand("log", "-1", "--format=%cI") }
    .getOrElse { Instant.now().toString() }

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outDir = layout.buildDirectory.dir("generated/build-info")
    outputs.dir(outDir)
    inputs.property("buildVersion", shadowJarVersion)
    inputs.property("buildTime", buildCommitTime)
    doLast {
        val props = Properties()
        props["buildVersion"] = shadowJarVersion
        props["buildTime"] = buildCommitTime
        outDir.get().asFile.mkdirs()
        outDir.get().file("orzmc-build.properties").asFile.outputStream().use { props.store(it, "OrzMC build info") }
    }
}
sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("generated/build-info"))
}
tasks.named("processResources") {
    dependsOn(generateBuildInfo)
}

// 统一通道名（小写），Hangar 和 Modrinth 共用
val platformChannel: String = if (isReleaseTag) "release" else "beta"

hangarPublish {
    publications.register("plugin") {
        version = shadowJarVersion
        channel = platformChannel
        changelog = changelogContent
        id = pluginYaml["name"] as String
        apiKey = System.getenv("HANGAR_API_TOKEN")
        platforms {
            paper {
                jar = tasks.shadowJar.flatMap { it.archiveFile }
                platformVersions = (property("plugin_support_paper_versions") as String).split(",").map { it.trim() }
            }
        }

        // 同步 README.md 到 Hangar 项目主页
        pages.resourcePage(project.file("README.md").readText())
    }
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set(System.getenv("MODRINTH_PROJECT_ID") ?: (property("modrinth_project_id") as String))
    versionNumber.set(shadowJarVersion)
    versionName.set(shadowJarVersion)
    versionType.set(platformChannel)
    changelog.set(changelogContent)
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(
        (property("plugin_support_paper_versions") as String)
            .split(",").map { it.trim() }
    )
    loaders.add("paper")
    // Folia 运行时与 Paper 共用同一 shadowJar（paper-plugin.yml 声明 folia-supported），
    // Modrinth 单独声明 loader 让 Folia 服务器玩家可见。
    // 注：Hangar 无 FOLIA 平台（仅 PAPER/VELOCITY/WATERFALL），兼容性由 PAPER 平台条目承载。
    loaders.add("folia")

    // 同步 README.md 到 Modrinth 项目主页
    syncBodyFrom.set(project.file("README.md").readText())
}

val debugServerVersion = property("plugin_debug_server_version") as String
tasks {
    register("installGitHooks") {
        doLast {
            serviceOf<ExecOperations>().exec {
                commandLine("git", "config", "core.hooksPath", ".githooks")
            }
            serviceOf<ExecOperations>().exec {
                commandLine("chmod", "+x", ".githooks/pre-commit")
            }
        }
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        // 启用弃用警告
        options.compilerArgs.add("-Xlint:deprecation")
        // 同时启用未检查的类型转换警告
        options.compilerArgs.add("-Xlint:unchecked")
    }
    // 配置工程内直接调试服务端插件
    // gradle-plugin: https://github.com/jpenilla/run-task#basic-usage
    val agreeEula = register("agreeEula") {
        doLast {
            val runDir = file("run")
            if (!runDir.exists()) {
                runDir.mkdirs()
            }
            val eulaFile = file("$runDir/eula.txt")
            eulaFile.writeText("eula=true\n")
        }
    }
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion(debugServerVersion)
        // 以离线模式启动服务端
        args("--nojline", "--nogui", "--online-mode=false")
        dependsOn(agreeEula)
    }
    // ---- Folia：runFolia（run-paper 3.1.0） + foliaSmoke 无头冒烟 ----
    val agreeFoliaEula = register("agreeFoliaEula") {
        doLast {
            val runDir = file("run-folia")
            if (!runDir.exists()) {
                runDir.mkdirs()
            }
            file("$runDir/eula.txt").writeText("eula=true\n")
        }
    }
    runPaper.folia.registerTask {
        // runDirectory 隔离到 run-folia/，避免 Folia 尝试加载 run/plugins 下
        // Paper 专属的 Vault/EssentialsX 等不兼容插件；shadowJar 由 run-paper 自动检测加入
        minecraftVersion(debugServerVersion)
        args("--nojline", "--nogui", "--online-mode=false")
        runDirectory.set(layout.projectDirectory.dir("run-folia"))
        dependsOn(agreeFoliaEula)
    }

    // ---- 真实服务端冒烟 / 自举 E2E ----
    // 设计要点：
    //  * 下载与启动拆成两个任务：download*Jar 有缓存（build/<dir>/，SHA256 通过才写标记），起服任务每次真实执行不跳过；
    //  * 不能嵌套调用 gradlew（会死锁在项目锁上）→ 自行解析 Fill API v3 + 直接 java -jar；
    //  * 控制台断言走 stdin，且只匹配「本次命令之后新增的日志」（避免命中早先日志造成假绿）。
    val foliaServerJar = registerServerJarDownload(debugServerVersion, "folia", "folia-smoke", "Folia")
    val paperServerJar = registerServerJarDownload(debugServerVersion, "paper", "paper-smoke", "Paper")
    // 真实依赖插件：LuckPerms（rank/review/权限链的唯一权威实现，MockBukkit 里没有）
    val luckPermsJar = registerModrinthPluginDownload(
        "downloadLuckPermsJar", "luckperms", debugServerVersion, "paper", "e2e-plugins", "LuckPerms"
    )

    register("foliaSmoke") {
        group = "verification"
        description = "真实 Folia 冒烟：启动 → 插件加载 → Bot 命令链路控制台断言 → stop → 断言干净退出"
        dependsOn("downloadFoliaJar", project.tasks.shadowJar)
        outputs.upToDateWhen { false } // 每次执行都真实启动，不因缓存跳过
        // -PfoliaImBackend=builtin：跑「builtin 模式干净起服」变体（backend=builtin + QQ 无凭据 → D3 停群路径）
        val builtinMode = providers.gradleProperty("foliaImBackend").orNull == "builtin"
        // 配置期解析（避免 doLast 内 Task.project 的执行期弃用告警 / 配置缓存不兼容）
        val runDir = project.layout.projectDirectory.dir("run-folia-smoke").asFile
        val shadowJarFile = project.tasks.shadowJar.get().archiveFile.get().asFile
        val javaBin = project.javaToolchains.launcherFor(project.java.toolchain).get().executablePath.asFile.absolutePath
        doLast {
            runHeadlessServerSmoke(
                core = "Folia",
                serverJar = foliaServerJar,
                runDir = runDir,
                shadowJarFile = shadowJarFile,
                javaBin = javaBin,
                port = 25580,
                prepare = { dataDir ->
                    if (builtinMode) {
                        // builtin 变体：backend=builtin 且 QQ 无凭据 → 应选 Unavailable 停群（D3）而非 EasyBot
                        File(dataDir, "im.yml").writeText(
                            "backend: builtin\nplatforms:\n  qq:\n    enabled: false\n    app_id: ''\n    client_secret: ''\n"
                        )
                    }
                },
                preChecks = if (builtinMode) {
                    listOf("builtin 停群分支（D3）" to Regex("停用群功能|无可用平台|backend=builtin"))
                } else {
                    emptyList()
                },
                checks = botConsoleChecks(uniqueE2eTag()),
            )
        }
    }

    // e2eLocalPaper / e2eLocalFolia：自举真实服务端 E2E（独立 run 目录与端口，可并行跑）。
    // 不依赖任何常驻测试服 / 面板 API —— 这是「让真实运行时层进 PR 门禁」的那一层。
    fun registerE2eLocal(
        taskName: String,
        core: String,
        downloadTask: String,
        serverJar: File,
        port: Int,
        extraPlugins: List<File> = emptyList(),
        extraChecks: List<ConsoleCheck> = emptyList(),
        extraTasks: List<String> = emptyList(),
        runDirSuffix: String = "",
    ) {
        register(taskName) {
            group = "verification"
            description = "自举真实 $core E2E：起服 → Bot 命令链路控制台断言 → 干净退出"
            dependsOn(downloadTask, *extraTasks.toTypedArray(), project.tasks.shadowJar)
            outputs.upToDateWhen { false }
            val runDir = project.layout.projectDirectory.dir("run-e2e-${core.lowercase()}$runDirSuffix").asFile
            val shadowJarFile = project.tasks.shadowJar.get().archiveFile.get().asFile
            val javaBin = project.javaToolchains.launcherFor(project.java.toolchain).get().executablePath.asFile.absolutePath
            doLast {
                runHeadlessServerSmoke(
                    core = core,
                    serverJar = serverJar,
                    runDir = runDir,
                    shadowJarFile = shadowJarFile,
                    javaBin = javaBin,
                    port = port,
                    prepare = {},
                    preChecks = if (extraPlugins.isEmpty()) {
                        emptyList()
                    } else {
                        listOf("LuckPerms 已加载" to Regex("LuckPerms v\\d"))
                    },
                    checks = botConsoleChecks(uniqueE2eTag()),
                    extraPlugins = extraPlugins,
                    extraChecks = extraChecks,
                )
            }
        }
    }
    registerE2eLocal("e2eLocalPaper", "Paper", "downloadPaperJar", paperServerJar, 25581)
    registerE2eLocal("e2eLocalFolia", "Folia", "downloadFoliaJar", foliaServerJar, 25582)
    registerE2eLocal(
        "e2eLocalPaperLp", "Paper", "downloadPaperJar", paperServerJar, 25583,
        extraPlugins = listOf(luckPermsJar), extraChecks = luckPermsChecks(),
        extraTasks = listOf("downloadLuckPermsJar"), runDirSuffix = "-lp",
    )
    registerE2eLocal(
        "e2eLocalFoliaLp", "Folia", "downloadFoliaJar", foliaServerJar, 25584,
        extraPlugins = listOf(luckPermsJar), extraChecks = luckPermsChecks(),
        extraTasks = listOf("downloadLuckPermsJar"), runDirSuffix = "-lp",
    )

    register("e2eLocal") {
        group = "verification"
        description = "自举真实服务端 E2E 入口（-Pcore=paper|folia，默认 folia）"
        val core = (providers.gradleProperty("core").orNull ?: "folia").lowercase()
        require(core == "paper" || core == "folia") { "非法 -Pcore=$core（仅支持 paper|folia）" }
        dependsOn(if (core == "paper") "e2eLocalPaper" else "e2eLocalFolia")
    }

    jar {
        enabled = false
    }
    shadowJar {
        minimize()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        archiveClassifier.set(null as String?)
        archiveVersion.set(shadowJarVersion)
        // LuckPerms API 由 LP 插件运行时提供，不打进 jar（避免类加载器冲突）
        exclude("net/luckperms/**")
    }
    build {
        dependsOn("shadowJar")
    }
    withType<Test> {
        useJUnitPlatform {
            // 隔离舱：`-PexcludeFlaky` 跳过 @Tag("flaky") 用例（已确认不稳定、正在修但暂不阻断 PR 时使用）；
            // 默认不跳过——隔离必须是显式动作，避免「静默丢失覆盖率」
            if (providers.gradleProperty("excludeFlaky").isPresent) {
                excludeTags("flaky")
            }
        }
        jvmArgs("-Xshare:off")
        // 偶发失败自动重跑（每用例最多 2 次）；重跑通过不判失败，但必须在 CI 日志里可见
        retry {
            maxRetries.set(2)
            maxFailures.set(50)
            failOnPassedAfterRetry.set(false)
        }
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    named<Copy>("processIntegrationTestResources") {
        duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    }
    register<Test>("integrationTest") {
        description = "Runs integration tests on a mocked Paper server."
        group = "verification"
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(test)
    }
}

// JaCoCo 报告输出（在 tasks {} 块外用 withType 避免 Kotlin DSL 接收者歧义）
tasks.withType<JacocoReport>().configureEach {
    dependsOn("test", "integrationTest")
    executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// JaCoCo 覆盖率验证门禁
tasks.withType<JacocoCoverageVerification>().configureEach {
    dependsOn("test", "integrationTest")
    executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    // 门槛贴着实测值留约 3–4 个百分点余量（2026-11 实测 INSTRUCTION 81.9% / BRANCH 68.9% / LINE 81.2%）。
    // 旧门槛 60/50/55 比现状低 20+ 点，只能拦灾难性回退，等于没有门禁——收紧到「小步回退也会红」。
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal.valueOf(0.78)
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = BigDecimal.valueOf(0.65)
            }
        }
        rule {
            limit {
                counter = "LINE"
                minimum = BigDecimal.valueOf(0.78)
            }
        }
    }
}

/**
 * 明文私钥/凭据扫描（防 2026-09 私钥误入库事故复发）：扫已跟踪文件，命中即失败。
 * 接入 `check` → CI（build workflow）与本地 `./gradlew check` 都会执行；人工全历史排查用
 * `bash scripts/check-secrets.sh --all-history`。
 */
val checkSecrets by tasks.registering(Exec::class) {
    group = "verification"
    description = "扫描已跟踪文件中的明文私钥/凭据（scripts/check-secrets.sh）"
    commandLine("bash", "${rootDir}/scripts/check-secrets.sh")
}

tasks.named("check") {
    dependsOn("integrationTest", "jacocoTestCoverageVerification", "checkSecrets")
}

