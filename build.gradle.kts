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
    add("integrationTestImplementation", "org.mockbukkit.mockbukkit:mockbukkit-v26.3:4.120.0")
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
private data class ConsoleCheck(
    val label: String,
    val command: String,
    val expect: Regex,
    val timeoutMs: Long = 30_000L,
    /** 需要"全新世界"才可断言：重复优化同一世界会命中已知缺陷（issue #588），本地复用 run 目录时跳过并说明。 */
    val requiresFreshWorld: Boolean = false,
)

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
    ConsoleCheck("审核列表（\$v l）", "orzdebug \$v l", Regex("待审核")),
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
    // 长任务（真实备份/优化链路）：小世界上各几秒；超时给足，避免慢机器误判
    ConsoleCheck("地图备份（\$b）", "orzdebug \$b", Regex("地图备份 完成"), 180_000L),
    // 世界复用（同目录第二次跑）也必须出完成回执：BUG #588 曾因 Done 事件计数器为 0 被跳过而无回执
    ConsoleCheck("地图优化（\$o）", "orzdebug \$o", Regex("地图优化\\s*完成"), 180_000L),
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
            // 优先 version_type=release（SNAPSHOT/beta 天天变，会让门禁无故漂移）；没有正式版才退而求其次
            val newest = versions.firstOrNull { it["version_type"] == "release" }
                ?: versions.firstOrNull()
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
            // 记录版本号：上游漂移探测用它判断「缓存 jar 是否落后于上游最新 release」
            markerFile.get().asFile.writeText(
                "sha512=$actualSha\nversion=${newest["version_number"]}\n"
            )
            logger.lifecycle("已下载 $displayName ${newest["version_number"]} → ${file.absolutePath}")
        }
    }
    return jarFile.get().asFile
}

/** 简单 HTTP GET（自举 E2E 的假 IM 平台控制面用；失败返回 null 便于轮询）。 */
private fun httpGetOrNull(url: String, timeoutMs: Int = 3000): String? =
    runCatching {
            val conn = URI(url).toURL().openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        .getOrNull()

/** 简单 HTTP POST JSON（同上；返回响应体或 null）。 */
private fun httpPostJsonOrNull(url: String, json: String, timeoutMs: Int = 3000): String? =
    runCatching {
            val conn = URI(url).toURL().openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        .getOrNull()

/** 轮询条件直到满足或超时；返回是否满足。 */
private fun awaitCondition(label: String, timeoutMs: Long, condition: () -> Boolean): Boolean {
    val start = System.nanoTime()
    while ((System.nanoTime() - start) < timeoutMs * 1_000_000L) {
        if (runCatching(condition).getOrDefault(false)) return true
        Thread.sleep(200)
    }
    println("等待超时: $label")
    return false
}

/**
 * 读日志 fromOffset 之后的全部文本。
 *
 * <p>统一口径：只看「本次命令之后」新增的行，绝不吃到历史/上一轮 run 的日志
 * （否则 run 目录复用时守卫会无端变红，或断言被旧行假绿）。</p>
 */
private fun logTextSince(logFile: File, fromOffset: Long): String {
    if (!logFile.exists()) return ""
    val length = logFile.length()
    if (length <= fromOffset) return ""
    val buf = ByteArray((length - fromOffset).toInt())
    RandomAccessFile(logFile, "r").use { raf ->
        raf.seek(fromOffset)
        raf.readFully(buf)
    }
    return String(buf, Charsets.UTF_8)
}

/**
 * 跑一个 node 探测脚本（e2e/ci 下的探针）：流式转发输出、硬超时、非 0 退出即抛错（让任务失败）。
 *
 * <p>为什么不用 Exec 任务：探测器必须在「服务端已起、还没 stop」的窗口里跑，只能由
 * {@code runHeadlessServerSmoke} 的 doLast 内联拉起；且失败信息要把服务端日志尾部一起抛出来。</p>
 *
 * <p>健壮性约定：无论正常结束 / 超时 / 被中断 / 读逻辑抛异常，`finally` 都会 `destroyForcibly()`
 * + 等退 + join 读线程，绝不留下 node 子进程（否则端口被占，下一次运行必然假失败）。</p>
 *
 * <p>参数约定：{@code label} 放在**最后**（带默认值的可选参数在前），调用点用具名调用 → 清晰且不怕再插入参数。</p>
 */
private fun runNodeProbe(
    logger: org.gradle.api.logging.Logger,
    projectDir: File,
    script: String,
    env: Map<String, String> = emptyMap(),
    timeoutMs: Long = 180_000,
    serverLog: File? = null,
    label: String,
) {
    logger.lifecycle("启动探测器: $script（$label，上限 ${timeoutMs / 1000}s）")
    val probe = try {
        ProcessBuilder("node", script).directory(projectDir).apply {
            environment().putAll(env)
        }.redirectErrorStream(true).start()
    } catch (e: IOException) {
        // 最常见的是 runner 上没装 Node：原始信息只有 `Cannot run program "node"`，不可操作
        throw GradleException(
            "无法启动 node 探测器（$label）：${e.message}\n" +
                "提示：本任务需要 Node.js 22+。本地先跑 `./gradlew npmCi` 安装 mineflayer，" +
                "并确认 `node -v` 可用（CI 由 actions/setup-node 提供）。",
        )
    }
    probe.outputStream.close()
    // 线程安全缓冲：读线程写入与主管道（超时/异常路径）读取并发，StringBuilder 会读到截断内容
    val output = StringBuffer()
    val readerThread =
        Thread {
                probe.inputStream.bufferedReader().forEachLine { line ->
                    output.append(line).append('\n')
                    logger.lifecycle("  [probe] $line")
                }
            }
            .apply {
                isDaemon = true
                start()
            }
    var timedOut = false
    var interrupted = false
    try {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!probe.isAlive) break
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                // 中断（Gradle 取消/守护进程退出）也必须先把子进程杀掉，否则 node 残留占端口
                interrupted = true
                break
            }
        }
        timedOut = !interrupted && probe.isAlive
    } finally {
        // 任何路径（正常/超时/中断/断言异常）都回收子进程与读线程，绝不留给下一次运行
        if (probe.isAlive) {
            probe.destroyForcibly()
            probe.waitFor(5, TimeUnit.SECONDS)
        }
        readerThread.join(2_000)
    }
    val exit = runCatching { probe.exitValue() }.getOrNull()
    if (interrupted) {
        Thread.currentThread().interrupt()
        throw InterruptedException("探测器被中断（$label），已回收子进程。输出:\n$output")
    }
    if (timedOut || exit == null) {
        throw GradleException(
            "探测器 ${timeoutMs}ms 未结束（$label），已回收子进程。输出:\n$output" + logHint(serverLog),
        )
    }
    if (exit != 0) {
        throw GradleException("探测器失败（exit=$exit，$label）。输出:\n$output" + logHint(serverLog))
    }
    logger.lifecycle("探测器通过: $label")
}

/** 探测器失败时附上服务端日志尾部（否则只剩「exit=1」毫无排查线索）。 */
private fun logHint(serverLog: File?): String =
    if (serverLog == null) "" else "\n服务器日志尾部:\n${logTail(serverLog)}"

/**
 * 确保 run 目录 `server.properties` 里某个键为指定值（读-改-写，幂等）。
 *
 * <p>为什么需要它：测试账号要在离线模式 + **非原版白名单**下才能登录。run 目录复用、或某一版
 * 服务端默认值变化（`white-list=true`）时，账号会在登录阶段被拒，探针只会看到「spawn 超时」——
 * 这条前置不显式保证，失败现象和真实缺陷（插件拒绝登录）无法区分。</p>
 */
private fun ensureServerProperty(runDir: File, key: String, value: String) {
    val file = File(runDir, "server.properties")
    if (!file.exists()) {
        file.writeText("$key=$value\n")
        return
    }
    val lines = file.readLines().toMutableList()
    val idx = lines.indexOfFirst { it.trimStart().startsWith("$key=") }
    if (idx >= 0) {
        if (lines[idx].trim() == "$key=$value") return
        lines[idx] = "$key=$value"
    } else {
        lines.add("$key=$value")
    }
    file.writeText(lines.joinToString("\n") + "\n")
}

/**
 * 把 OrzMC 的 `config.yml` 里 `whitelist.force_whitelist` 改成 false（读-改-写，幂等）。
 *
 * <p>为什么不用 `appendText("whitelist:\n  force_whitelist: false\n")`：① 基础配置几乎必然已有
 * `whitelist:` 段，顶层重复键的 YAML 语义不保证后写的段覆盖先写的；② 追加不带前导换行会把末行
 * 拼坏（`...truewhitelist:`）；③ run 目录复用时每次再追加一份。这里改成：已有 `force_whitelist:`
 * 就改值，有 `whitelist:` 段就插到段内，两者都没有才追加新段——其余内容一行不动。</p>
 */
private fun disableForceWhitelist(configFile: File) {
    val text = if (configFile.exists()) configFile.readText() else ""
    val lines = text.lines().toMutableList()
    val key = "force_whitelist"
    val keyIdx = lines.indexOfFirst { it.matches(Regex("""^\s*$key\s*:.*$""")) }
    if (keyIdx >= 0) {
        val indent = lines[keyIdx].takeWhile { it == ' ' || it == '\t' }
        lines[keyIdx] = "$indent$key: false"
        configFile.writeText(lines.joinToString("\n"))
        return
    }
    val sectionIdx = lines.indexOfFirst { it.matches(Regex("""^whitelist\s*:.*$""")) }
    if (sectionIdx >= 0) {
        // 段内缩进 2 空格，与仓库默认配置一致
        lines.add(sectionIdx + 1, "  $key: false")
        configFile.writeText(lines.joinToString("\n"))
        return
    }
    val out =
        buildString {
            append(text.trimEnd('\n'))
            if (isNotEmpty()) append('\n')
            append("whitelist:\n  $key: false\n")
        }
    configFile.writeText(out)
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
    extraCommands: List<String> = emptyList(),
    afterReady: (File) -> Unit = {},
    /** 性能快照标签（如 paper / folia-lp / im）；null 表示不采集。 */
    perfLabel: String? = null,
    /**
     * 起服就绪 + {@code extraCommands} 之后、控制台断言之前执行的自定义校验；第二个参数是
     * 「再向该服务端 stdin 写一条命令」的通道。
     *
     * <p>为什么需要它：权限/传送门两个任务必须先让玩家登录一次（LP 只能按名字解析登录过的玩家）
     * 再下发 LuckPerms 命令、等 LP 异步结果落地，最后才跑探测器——单次 {@code extraCommands}
     * 列表做不到「跑一段 node → 发命令 → 再跑一段 node」的编排。</p>
     */
    afterReadyConsole: ((File, (String) -> Unit) -> Unit)? = null,
) {
    runDir.mkdirs()
    // 世界是否全新：部分断言（重复优化）只在全新世界上成立（已知缺陷见 issue #588）
    val worldFresh = !File(runDir, "world").exists()
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
    // 空 deny-list：让 stop 不被安全加固拦截；打开 optimize_enabled：让 $o 覆盖真实优化链路而非"未启用"分支
    File(dataDir, "config.yml")
        .writeText("guard:\n  blocked_commands: []\nmaintenance:\n  optimize_enabled: true\n")
    prepare(dataDir)

    val logFile = File(runDir, "smoke.log").apply { if (exists()) delete() }
    // 用 toolchain JDK 启动（Folia 26.3 最低要求 Java 25），不依赖 Gradle 守护进程 JDK
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

    fun abort(message: String): Nothing = throw GradleException("$message\n日志尾部:\n${logTail(logFile)}")

    // 任何异常（含 afterReady 里的断言失败）都必须回收服务端进程，否则端口被占、后续运行必然假失败
    try {

    val startedMs = waitForLog(logFile, Regex("Done \\(.*\\)"), 240_000)
    if (startedMs < 0) abort("$core 服务器 240s 内未完成启动。")
    logger.lifecycle("$core 已启动（${startedMs}ms）。校验 OrzMC 加载...")
    if (waitForLog(logFile, Regex("OrzMC"), 15_000) < 0) abort("OrzMC 插件未加载。")

    for ((label, pattern) in preChecks) {
        logger.lifecycle("校验: $label")
        if (!awaitLogAfter(logFile, 0L, pattern, 30_000)) abort("未通过: $label（期望 ${pattern.pattern}）")
    }

    // 起服就绪后先发无断言的控制台命令（例如 op 测试玩家）
    for (command in extraCommands) {
        logger.lifecycle("控制台命令（准备）: $command")
        proc.outputStream.write((command + "\n").toByteArray(Charsets.UTF_8))
        proc.outputStream.flush()
        Thread.sleep(150)
    }

    // 起服就绪后的自定义校验（例如假 IM 平台：等通道连上 → 注入入站消息 → 断言出站）
    afterReady(logFile)

    // 需要「服务端在跑、还能继续发控制台命令」编排的任务（权限三档 / 传送门）走这里
    if (afterReadyConsole != null) {
        val sendConsole: (String) -> Unit = { command ->
            logger.lifecycle("控制台命令（探测编排）: $command")
            try {
                proc.outputStream.write((command + "\n").toByteArray(Charsets.UTF_8))
                proc.outputStream.flush()
            } catch (e: IOException) {
                abort("无法向服务器写入命令（进程可能已退出）: $command（${e.message}）")
            }
        }
        afterReadyConsole(logFile, sendConsole)
    }

    var executed = 0
    var skipped = 0
    var backupMs: Long? = null
    for (check in checks + extraChecks) {
        if (check.requiresFreshWorld && !worldFresh) {
            skipped++
            logger.lifecycle("跳过断言（run 目录复用、非全新世界）: ${check.label}")
            continue
        }
        executed++
        logger.lifecycle("控制台断言: ${check.label} → ${check.command}")
        val checkStart = System.currentTimeMillis()
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
        if (check.command.contains("orzdebug \$b")) {
            backupMs = System.currentTimeMillis() - checkStart
        }
    }

    if (perfLabel != null && startedMs > 0) {
        val perfFile = File("build/quality/perf-$perfLabel.json")
        perfFile.parentFile.mkdirs()
        perfFile.writeText(
            "{\"core\": \"$core\", \"label\": \"$perfLabel\", \"boot_ms\": $startedMs, " +
                "\"backup_ms\": ${backupMs ?: "null"}}\n"
        )
        logger.lifecycle("性能快照: boot=${startedMs}ms backup=${backupMs ?: "-"}ms → ${perfFile.path}")
    }

    // $b 已在断言表内执行：再校验备份 zip 真的落盘（防"日志说完成但没文件"）
    if ((checks + extraChecks).any { it.command.contains("orzdebug \$b") }) {
        val backupDir = File(runDir, "backup")
        val zips = backupDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.toList().orEmpty()
        if (zips.isEmpty()) abort("备份未落盘：$backupDir 下没有 zip")
        val newestZip = zips.maxByOrNull { it.lastModified() }!!
        if (newestZip.length() < 10_000) abort("备份 zip 过小（${newestZip.length()} 字节）: ${newestZip.name}")
        logger.lifecycle("备份落盘校验通过: ${newestZip.name}（${newestZip.length() / 1024} KB）")
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
    logger.lifecycle(
        "$core 冒烟通过：插件加载 + $executed 条控制台断言" +
            (if (skipped > 0) "（跳过 $skipped 条：run 目录复用）" else "") +
            " + 干净退出（exit 0）"
    )
    } finally {
        if (proc.isAlive) {
            proc.destroyForcibly()
            proc.waitFor(30, TimeUnit.SECONDS)
        }
    }
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
    // 协议兼容（2026-11 实测）：mineflayer 最高 1.21.11，服务端 26.3 → 需要 **ViaVersion + ViaBackwards**
    // （新客户端连旧服务端是 backwards 方向；只装 ViaVersion 或只用协议补丁都会被拒 "Outdated client"）
    val viaVersionJar = registerModrinthPluginDownload(
        "downloadViaVersionJar", "viaversion", debugServerVersion, "paper", "e2e-plugins", "ViaVersion"
    )
    val viaBackwardsJar = registerModrinthPluginDownload(
        "downloadViaBackwardsJar", "viabackwards", debugServerVersion, "paper", "e2e-plugins", "ViaBackwards"
    )
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
        perfLabel: String? = if (extraPlugins.isEmpty()) core.lowercase() + runDirSuffix else null,
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
                    perfLabel = perfLabel,
                )
            }
        }
    }
    registerE2eLocal("e2eLocalPaper", "Paper", "downloadPaperJar", paperServerJar, 25581)
    registerE2eLocal("e2eLocalFolia", "Folia", "downloadFoliaJar", foliaServerJar, 25582)
    registerE2eLocal(
        "e2eLocalPaperLp", "Paper", "downloadPaperJar", paperServerJar, 25583,
        extraPlugins = listOf(luckPermsJar), extraChecks = luckPermsChecks(),
        extraTasks = listOf("downloadLuckPermsJar"), runDirSuffix = "-lp", perfLabel = "paper-lp",
    )
    registerE2eLocal(
        "e2eLocalFoliaLp", "Folia", "downloadFoliaJar", foliaServerJar, 25584,
        extraPlugins = listOf(luckPermsJar), extraChecks = luckPermsChecks(),
        extraTasks = listOf("downloadLuckPermsJar"), runDirSuffix = "-lp", perfLabel = "folia-lp",
    )

    // e2eLocalPlayer：真实玩家侧自举（mineflayer 客户端 + ViaVersion 向下兼容）
    // 覆盖此前只能真人点的路径：加入服务器、/menu 箱子 GUI、非管理员命令被拒、/guide 发书。
    register("e2eLocalPlayer") {
        group = "verification"
        description = "自举玩家侧 E2E：真实客户端连接 → 加入/GUI/权限隔离/新手书（ViaVersion 兼容协议）"
        dependsOn("downloadPaperJar", "downloadViaVersionJar", "downloadViaBackwardsJar", "npmCi", project.tasks.shadowJar)
        outputs.upToDateWhen { false }
        val runDir = project.layout.projectDirectory.dir("run-e2e-player").asFile
        val shadowJarFile = project.tasks.shadowJar.get().archiveFile.get().asFile
        val javaBin = project.javaToolchains.launcherFor(project.java.toolchain).get().executablePath.asFile.absolutePath
        val probePort = 25586
        val probeScript = "e2e/ci/player-probe.js"
        val probeVersion = "1.21.11"
        val projectDirPath = project.layout.projectDirectory.asFile
        doLast {
            runHeadlessServerSmoke(
                core = "Paper",
                serverJar = paperServerJar,
                runDir = runDir,
                shadowJarFile = shadowJarFile,
                javaBin = javaBin,
                port = probePort,
                prepare = { dataDir ->
                    // 玩家要能进来：关掉强制白名单；其余（含维护/优化开关）沿用 runner 写入的基础配置
                    File(dataDir, "config.yml")
                        .appendText("whitelist:\n  force_whitelist: false\n")
                },
                preChecks = listOf(
                    "ViaBackwards 已加载" to Regex("ViaBackwards"),
                    // 映射加载完之前登录会被按原版协议拒绝（"Outdated client"），必须等这一行
                    "协议映射就绪" to Regex("Finished mapping loading"),
                ),
                checks = emptyList(), // 玩家任务只跑客户端探测（控制台断言由其它任务覆盖）
                extraCommands = listOf("op E2EAdmin"), // 管理员玩家：GUI/新手书需要权限；非管理员玩家用于验证隔离
                perfLabel = "player",
                extraPlugins = listOf(viaVersionJar, viaBackwardsJar),
                afterReady = {
                    logger.lifecycle("启动玩家探测器: $probeScript（client $probeVersion → 服务端 $probePort）")
                    val probe =
                        ProcessBuilder("node", probeScript)
                            .directory(projectDirPath)
                            .apply {
                                environment()["MC_PORT"] = probePort.toString()
                                environment()["MC_VERSION"] = probeVersion
                            }
                            .redirectErrorStream(true)
                            .start()
                    probe.outputStream.close()
                    val reader = probe.inputStream.bufferedReader()
                    val output = StringBuilder()
                    var finished = false
                    val deadline = System.currentTimeMillis() + 180_000
                    val readerThread = Thread {
                        reader.forEachLine { line ->
                            output.appendLine(line)
                            logger.lifecycle("  [probe] $line")
                        }
                    }
                    readerThread.start()
                    while (System.currentTimeMillis() < deadline) {
                        if (!probe.isAlive) {
                            finished = true
                            break
                        }
                        Thread.sleep(200)
                    }
                    if (!finished) {
                        probe.destroyForcibly()
                        throw GradleException("玩家探测器 180s 未结束。输出:\n$output")
                    }
                    readerThread.join(5_000)
                    val exit = probe.exitValue()
                    if (exit != 0) {
                        throw GradleException(
                            "玩家探测器失败（exit=$exit）。输出:\n$output\n服务器日志尾部:\n${logTail(File(runDir, "smoke.log"))}"
                        )
                    }
                    logger.lifecycle("玩家侧校验通过（加入 / GUI / 权限隔离 / 新手书）")
                },
            )
        }
    }

    // e2eLocalPerm：权限三档矩阵（default/member/builder + op 对照）
    // 覆盖的是 MockBukkit 测不到的那一层：真实 LuckPerms 的组/继承/节点解析 + 真实 Brigadier
    // requires() 过滤 + 拦截器运行时拒绝文案。客户端侧断言（谁被拒、谁放行）由 node 探针完成。
    register("e2eLocalPerm") {
        group = "verification"
        description = "自举权限三档 E2E：真实 LP 建组挂节点 → 三档账号 + op 对照 → 逐档断言 OrzMC 命令放行/拒绝"
        dependsOn(
            "downloadPaperJar",
            "downloadLuckPermsJar",
            "downloadViaVersionJar",
            "downloadViaBackwardsJar",
            "npmCi",
            project.tasks.shadowJar,
        )
        outputs.upToDateWhen { false }
        val runDir = project.layout.projectDirectory.dir("run-e2e-perm").asFile
        val shadowJarFile = project.tasks.shadowJar.get().archiveFile.get().asFile
        val javaBin = project.javaToolchains.launcherFor(project.java.toolchain).get().executablePath.asFile.absolutePath
        val probePort = 25587
        val probeScript = "e2e/ci/perm-tiers-probe.js"
        // 与传送门任务同样的原因：1.21.x 客户端在 ViaBackwards 下会被判非法移动而踢人
        val probeVersion = "1.20.4"
        val projectDirPath = project.layout.projectDirectory.asFile
        val serverLog = File(runDir, "smoke.log")
        // 探针自身上限（见探针文件头的「各步之和 × 1.5 + 30s」公式）：
        //  · register：4 账号 × spawn 30s + 收尾 10s ≈ 130s → 130×1.5+30 ≈ 225s
        //  · assert  ：4 账号 × spawn 30s + 13 条断言 × 8s + 4 个「未拒绝」窗口 × 3s ≈ 238s
        //              → 238×1.5+30 ≈ 390s
        // Gradle 侧一律比探针自身上限再宽 30~70s：让探针自己先超时退出，才能报出「卡在哪一步」
        // （直接被子进程超时干掉只会得到「未结束」三个字，连哪条断言失败都看不到）。
        val registerProbeTimeoutMs = 260_000L
        val assertProbeTimeoutMs = 460_000L
        doLast {
            val probeEnv = mapOf("MC_PORT" to probePort.toString(), "MC_VERSION" to probeVersion)
            runHeadlessServerSmoke(
                core = "Paper",
                serverJar = paperServerJar,
                runDir = runDir,
                shadowJarFile = shadowJarFile,
                javaBin = javaBin,
                port = probePort,
                prepare = { dataDir ->
                    // 测试账号要能进来：原版白名单（server.properties）+ 插件强制白名单都要关。
                    // 两者都是读-改-写幂等写入（run 目录复用不会堆叠、也不会拼坏末行）。
                    ensureServerProperty(runDir, "white-list", "false")
                    disableForceWhitelist(File(dataDir, "config.yml"))
                },
                preChecks = listOf(
                    "LuckPerms 已加载" to Regex("LuckPerms v\\d"),
                    "ViaBackwards 已加载" to Regex("ViaBackwards"),
                    // 映射加载完之前登录会被按原版协议拒绝（"Outdated client"），必须等这一行
                    "协议映射就绪" to Regex("Finished mapping loading"),
                ),
                checks = emptyList(), // 本任务的断言全部在客户端侧（探针）完成
                perfLabel = "perm",
                extraPlugins = listOf(viaVersionJar, viaBackwardsJar, luckPermsJar),
                afterReadyConsole = { logFile, console ->
                    // ① 登记：LuckPerms 只能按名字解析「登录过」的玩家（实测：未登录过的名字
                    //    执行 lp user X parent set ... 只会回 `[LP] A user for X could not be found.`），
                    //    所以先让四个账号各登录一次。
                    runNodeProbe(
                        logger = logger,
                        projectDir = projectDirPath,
                        script = probeScript,
                        env = probeEnv + ("MC_PERM_PHASE" to "register"),
                        timeoutMs = registerProbeTimeoutMs,
                        serverLog = serverLog,
                        label = "权限矩阵-登记登录",
                    )

                    // ② 建组/挂继承/挂节点/挂账号：LP 命令是异步的，按「本次发出的 N 条命令都打印了
                    //    一行 [LP] 结果」同步，绝不靠固定 sleep（慢机器上会偶发「组还没生效就断言」）。
                    val lpFrom = stableLogOffset(logFile)
                    console("op E2EOp") // op 对照组：requires() 阶段判 isOp 的硬编码 admin 命令靠它放行
                    // 复用 run 目录时该账号已经是 op，原版会回「Nothing changed. … already an operator」
                    if (!awaitLogAfter(logFile, lpFrom, Regex("Made E2EOp a server operator|is already an operator"), 30_000)) {
                        throw GradleException("op E2EOp 未生效（30s 内日志没有操作员变更记录）")
                    }
                    val lpCommands = listOf(
                        "lp creategroup member",
                        "lp creategroup builder",
                        "lp group member parent set default",
                        "lp group builder parent set member",
                        // 三档矩阵的「档位差异」就靠这个节点：builder 档拿到 orzmc.admin → /portal、/bot http 放行
                        "lp group builder permission set orzmc.admin true",
                        "lp user E2EDefault parent set default",
                        "lp user E2EMember parent set member",
                        "lp user E2EBuilder parent set builder",
                    )
                    lpCommands.forEach(console)
                    // 只统计「真正执行完」的结果行：LP 在并发下发时会先打印
                    // 「目前有另一個指令正在執行，正在等待該指令執行完成……」这类排队提示，
                    // 它们同样带 [LP] 前缀——按裸 [LP] 计数会让屏障在命令还没执行时提前通过。
                    val lpQueued = Regex("正在等待|目前有另一個指令|is already running|already executing|waiting for the other")
                    fun lpOutcomeLines(): List<String> =
                        logTextSince(logFile, lpFrom)
                            .lines()
                            .filter { "[LP]" in it && !lpQueued.containsMatchIn(it) }
                    val lpDone = awaitCondition("LuckPerms 命令全部落地", 90_000) {
                        lpOutcomeLines().size >= lpCommands.size
                    }
                    if (!lpDone) {
                        throw GradleException(
                            "90s 内 LuckPerms 只输出了 ${lpOutcomeLines().size}/${lpCommands.size} 条结果。" +
                                "服务器日志尾部:\n${logTail(logFile)}"
                        )
                    }
                    // 「行数到齐」≠「命令执行成功」：参数错/组名拼错/权限不足同样会打印一行 [LP]。
                    // 所以再断言「没有命中错误形态的行」，并把命中行原文打出来。
                    // 白名单只留两类（评审要求）：
                    //  · already exists / 已存在（creategroup 重跑的幂等预期，LP 输出跟随服务端语言，中英都覆盖）
                    //  · 登记阶段之前的 `A user for .* could not be found`——窗口 lpFrom 落在登记之后，
                    //    这类行天然不在窗口内；**窗口内出现即账号没登记成功，是真失败**，不白名单。
                    val lpWhitelist = Regex("already exists|已存在|already has|已設定|已經設定")
                    val lpUnresolved = Regex("A user for .* could not be found|找不到名為|找不到名为")
                    val lpError =
                        Regex(
                            "could not be found|找不到名為|找不到名为|does not exist|不存在|" +
                                "occured whilst executing|發生錯誤|发生错误|Unknown command|未知的指令|" +
                                "do not have permission|沒有權限|没有权限",
                        )
                    val lpLines = lpOutcomeLines()
                    val unresolvedLines = lpLines.filter { lpUnresolved.containsMatchIn(it) }
                    if (unresolvedLines.isNotEmpty()) {
                        throw GradleException(
                            "LuckPerms 解析不到测试账号（${unresolvedLines.size} 条）——登记阶段没有把名字写进 usercache。" +
                                "命中行原文:\n${unresolvedLines.joinToString("\n") { "  | $it" }}" +
                                "\n服务器日志尾部:\n${logTail(logFile)}"
                        )
                    }
                    val lpOffenders = lpLines.filter { line ->
                        lpError.containsMatchIn(line) && !lpWhitelist.containsMatchIn(line)
                    }
                    if (lpOffenders.isNotEmpty()) {
                        throw GradleException(
                            "LuckPerms 有 ${lpOffenders.size} 条命令报错（行数到齐不代表落地成功）。" +
                                "命中行原文:\n${lpOffenders.joinToString("\n") { "  | $it" }}" +
                                "\n服务器日志尾部:\n${logTail(logFile)}"
                        )
                    }
                    logger.lifecycle("LuckPerms 已挂好三档组与 orzmc.admin 节点（${lpCommands.size} 条命令落地，0 条报错）")

                    // ③ 矩阵断言：四个账号依次登录 → 逐档断言放行/拒绝（真实文案，见探针头部注释）
                    runNodeProbe(
                        logger = logger,
                        projectDir = projectDirPath,
                        script = probeScript,
                        env = probeEnv + ("MC_PERM_PHASE" to "assert"),
                        timeoutMs = assertProbeTimeoutMs,
                        serverLog = serverLog,
                        label = "权限三档矩阵断言",
                    )
                },
            )
        }
    }

    // e2eLocalPortal：村民穿过下界传送门（实体传送策略必须对 EntityPortalEvent 放行）
    // 覆盖的是「策略开关与传送门边界」这条只有真实服务端才会走到的路径：MockBukkit 里没有
    // 原版门判定，单测只能证明策略函数返回值，证明不了村民真的会过去。
    register("e2eLocalPortal") {
        group = "verification"
        description = "自举传送门 E2E：控制台建 4×5 下界门 → 召唤村民 → 断言村民离开主世界并到达地狱维度"
        dependsOn(
            "downloadPaperJar",
            "downloadViaVersionJar",
            "downloadViaBackwardsJar",
            "npmCi",
            project.tasks.shadowJar,
        )
        outputs.upToDateWhen { false }
        val runDir = project.layout.projectDirectory.dir("run-e2e-portal").asFile
        val shadowJarFile = project.tasks.shadowJar.get().archiveFile.get().asFile
        val javaBin = project.javaToolchains.launcherFor(project.java.toolchain).get().executablePath.asFile.absolutePath
        val probePort = 25588
        val probeScript = "e2e/ci/entity-portal-probe.js"
        // 客户端版本必须是 1.20.4：1.21.x 客户端经 ViaBackwards 连 26.3 会被服务端判为
        // 「Invalid move player packet received」踢人，且拿不到区块数据（详见探针文件头）
        val probeVersion = "1.20.4"
        val projectDirPath = project.layout.projectDirectory.asFile
        val serverLog = File(runDir, "smoke.log")
        // 探针自身上限（见探针文件头公式）：spawn 30 + 清场 20 + 建门 3 + 门框同步 15 + 在场 8
        // + 点亮 15 + 过门 45 + 地狱 10 + 清理 10 = 156s → 156×1.5+30 ≈ 264s。
        // Gradle 侧留到 300s（> 探针自身上限）：让探针自己先超时并报出「卡在哪一步」。
        val probeTimeoutMs = 300_000L
        doLast {
            runHeadlessServerSmoke(
                core = "Paper",
                serverJar = paperServerJar,
                runDir = runDir,
                shadowJarFile = shadowJarFile,
                javaBin = javaBin,
                port = probePort,
                prepare = { dataDir ->
                    // 探针只用**原生命令**（不靠 /orzdebug $e）；探针账号要能进来，原版白名单与插件
                    // 强制白名单都得关（读-改-写幂等，支持 run 目录复用）
                    ensureServerProperty(runDir, "white-list", "false")
                    disableForceWhitelist(File(dataDir, "config.yml"))
                },
                preChecks = listOf(
                    "ViaBackwards 已加载" to Regex("ViaBackwards"),
                    "协议映射就绪" to Regex("Finished mapping loading"),
                ),
                // 本任务**没有**服务端侧断言：结构/实体/清理都在客户端侧（探针）完成。
                // 曾有一条「日志里不得出现『实体传送被禁用:<村民名>』」的守卫，已删——理由见下方注释。
                checks = emptyList(),
                perfLabel = "portal",
                extraPlugins = listOf(viaVersionJar, viaBackwardsJar),
                afterReadyConsole = { logFile, console ->
                    // /orzdebug 只对 op 或 orzmc.admin 可见：这里不需要 LuckPerms，直接 op 探针账号
                    val probeFrom = stableLogOffset(logFile)
                    console("op E2EPortal")
                    // 复用 run 目录时该账号已经是 op，原版会回「Nothing changed. … already an operator」，
                    // 两种结果都算成功（否则第二次运行必挂）
                    if (!awaitLogAfter(logFile, probeFrom, Regex("Made E2EPortal a server operator|is already an operator"), 30_000)) {
                        throw GradleException("op E2EPortal 未生效（30s 内日志没有操作员变更记录）")
                    }
                    runNodeProbe(
                        logger = logger,
                        projectDir = projectDirPath,
                        script = probeScript,
                        env = mapOf(
                            "MC_PORT" to probePort.toString(),
                            "MC_VERSION" to probeVersion,
                        ),
                        timeoutMs = probeTimeoutMs,
                        serverLog = serverLog,
                        label = "村民过传送门",
                    )
                    // 为什么**没有**「日志里不得出现『实体传送被禁用』」的守卫（2026-11 评审决定删除，不是收窄）：
                    //  ① 它天然不可靠：实体名来自 `entity.getName()`/`getType().name()`，实际可能是
                    //     `Villager`/`VILLAGER`/`minecraft:villager`，按大小写敏感的裸串/收窄串匹配
                    //     很可能恒绿（看似保护、实则从不生效）；
                    //  ② 扫描从 offset 0 开始，run 目录复用时上一轮日志会污染判定（无端变红）；
                    //  ③ 它无法自证存活：命中与不命中都可能是真实情况。
                    // 行为断言已经覆盖了同一事实：村民在**原维度消失** + `execute in the_nether` 命中——
                    // 若被 `EntityPortalEvent`/`OrzTPEvent` 拦下，村民根本不会过去（探针会直接报失败）。
                    // 历史记录：2026-11-16 CI 曾因同一句里无关实体（`Crimson Roots`）误红，
                    // 证明裸串与「只匹配村民」的收窄串都不可靠。
                    logger.lifecycle("传送门校验通过（门结构合法 + 村民过门 + 到达地狱维度 + 产物已清理）")
                },
            )
        }
    }

    // e2eLocalIm：builtin QQ 通道 + 仓库内假平台的全链路自举（token→gateway→WS→入站→命令→出站）。
    // 这是此前唯一完全无法自动化的一层（真实 QQ 频控/窗口不可仿造，但协议与业务链路可以）。
    // 玩家探测器依赖（mineflayer）：按 package-lock 安装到 e2e/node_modules，lock 变更才重装
    register<Exec>("npmCi") {
        group = "verification"
        workingDir = project.layout.projectDirectory.dir("e2e").asFile
        commandLine("npm", "ci", "--no-audit", "--no-fund")
        inputs.file(project.layout.projectDirectory.file("e2e/package-lock.json"))
        outputs.dir(project.layout.projectDirectory.dir("e2e/node_modules"))
    }

    register("e2eLocalIm") {
        group = "verification"
        description = "自举 builtin IM 全链路：假 QQ 平台（本机）→ 起服 → 注入群消息 → 断言出站回复"
        dependsOn("downloadPaperJar", project.tasks.shadowJar)
        outputs.upToDateWhen { false }
        val runDir = project.layout.projectDirectory.dir("run-e2e-im").asFile
        val shadowJarFile = project.tasks.shadowJar.get().archiveFile.get().asFile
        val javaBin = project.javaToolchains.launcherFor(project.java.toolchain).get().executablePath.asFile.absolutePath
        val fakePort = 18080
        // 注入的群指令（`$h`）：用拼接避免 Kotlin 模板转义把内容变成空串
        val imProbeCommand = "$" + "h"
        val fakeBase = "http://127.0.0.1:$fakePort"
        val projectDirPath = project.layout.projectDirectory.asFile
        doLast {
            val fakeLog = File(project.layout.buildDirectory.get().asFile, "fake-qq.jsonl")
            val fakeOut = File(project.layout.buildDirectory.get().asFile, "fake-qq.out")
            fakeOut.parentFile.mkdirs()
            val fake =
                ProcessBuilder(
                        "python3",
                        "scripts/e2e/fake_qq_platform.py",
                        "--port",
                        fakePort.toString(),
                        "--log",
                        fakeLog.absolutePath,
                    )
                    .directory(projectDirPath)
                    .redirectErrorStream(true)
                    .redirectOutput(fakeOut)
                    .start()
            try {
                if (!awaitCondition("假 QQ 平台启动", 20_000) { httpGetOrNull("$fakeBase/control/health") != null }) {
                    throw GradleException("假 QQ 平台未启动。输出:\n${fakeOut.readText()}")
                }
                logger.lifecycle("假 QQ 平台已启动: $fakeBase")
                runHeadlessServerSmoke(
                    core = "Paper",
                    serverJar = paperServerJar,
                    runDir = runDir,
                    shadowJarFile = shadowJarFile,
                    javaBin = javaBin,
                    port = 25585,
                    prepare = { dataDir ->
                        File(dataDir, "im.yml")
                            .writeText(
                                """
                                backend: builtin
                                platforms:
                                  qq:
                                    enabled: true
                                    app_id: 'e2e-app'
                                    client_secret: 'e2e-secret'
                                    api_base: '$fakeBase'
                                """
                                    .trimIndent() + "\n"
                            )
                        File(dataDir, "im_bindings.yml")
                            .writeText(
                                """
                                sessions:
                                  qq:
                                    admin_group: 'group:G1'
                                """
                                    .trimIndent() + "\n"
                            )
                    },
                    preChecks = emptyList(),
                    checks = botConsoleChecks(uniqueE2eTag()),
                    perfLabel = "im",
                    afterReady = { logFile ->
                        // 等 builtin QQ 通道真正 READY（日志为准；WS 握手早于 identify，不能只看连接数）
                        // 从文件头开始找：READY 可能在本步之前就已经打印（smoke.log 每次运行都会重建，无残留风险）
                        if (!awaitLogAfter(logFile, 0L, Regex("网关 READY|会话已建立"), 90_000)) {
                            throw GradleException("90s 内 QQ 通道未 READY（检查 im.yml / api_base / 启动日志）")
                        }
                        // 注入管理群消息（$h）：最多重试 3 次（首次可能早于插件命令层就绪）
                        var ok = false
                        for (attempt in 1..3) {
                            val injected =
                                httpPostJsonOrNull(
                                    "$fakeBase/control/message",
                                    """{"content":"$imProbeCommand","group_openid":"G1","member_openid":"U1","role":"owner"}""",
                                )
                            if (injected == null) throw GradleException("注入入站消息失败（控制面不可用）")
                            logger.lifecycle("已注入入站消息（第 $attempt 次）: $injected")
                            ok =
                                awaitCondition("出站回复到达假平台", 20_000) {
                                    val sent = httpGetOrNull("$fakeBase/control/sent") ?: ""
                                    sent.contains("/v2/groups/G1/messages") && sent.contains("群指令帮助")
                                }
                            if (ok) break
                        }
                        if (!ok) {
                            throw GradleException(
                                "出站回复未出现。假平台记录:\n${httpGetOrNull("$fakeBase/control/sent")}" +
                                    "\n服务器日志尾部:\n${logTail(File(runDir, "smoke.log"))}"
                            )
                        }
                        logger.lifecycle("IM 链路校验通过：入站 $imProbeCommand → 命令层 → 出站到 group:G1")
                    },
                )
            } finally {
                fake.destroyForcibly()
            }
        }
    }

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
        // E2eCommandCoverageTest 直接解析本脚本的断言表 → 声明为测试输入：脚本改动时测试必须重跑，
        // 否则「删掉一条断言」这种变更会被 Gradle 判为 up-to-date 而漏检
        inputs.file("build.gradle.kts").withPropertyName("e2eAssertionSource")
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
    // 门槛贴着实测值留约 3–4 个百分点余量（2026-11 实测 INSTRUCTION 85.4% / BRANCH 73.6% / LINE 84.9%）。
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
                minimum = BigDecimal.valueOf(0.72)
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


// =====================================================================
// 变异测试（Pitest）：nightly-only，**不进 PR 门禁**
// ---------------------------------------------------------------------
// 覆盖率只说"这行跑过"，变异测试才说"这个分支有没有断言把它钉住"。
// 代价是慢（每个变异体重跑相关测试），所以只在夜间对一小片目标跑；
// 结果用于发现"测试很多但断言很弱"的地方，不阻塞任何流水线。
//
//   ./gradlew pitest                                   # 默认目标（tnt + rank）
//   ./gradlew pitest -PpitestTargets=com.x.Pkg.*       # 指定类/包（逗号分隔）
//   ./gradlew pitest -PpitestMutators=STRONGER         # 换变异算子
// =====================================================================
val pitestTargetsSpec = (findProperty("pitestTargets") as String?)
    ?: "com.jokerhub.paper.plugin.orzmc.features.tnt.*,com.jokerhub.paper.plugin.orzmc.features.rank.*"
val pitestMutatorsSpec = (findProperty("pitestMutators") as String?) ?: "DEFAULTS"
val pitestThreadsSpec = ((findProperty("pitestThreads") as String?) ?: "4").toInt()

// 用命令行 main 直接跑（不引入 Gradle 插件，避免插件与 Gradle 9.x 的兼容绑定）。
val pitestRuntime: Configuration = configurations.maybeCreate("pitestRuntime").apply {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val pitestEngineVersion = "1.30.2"
dependencies {
    add("pitestRuntime", "org.pitest:pitest:$pitestEngineVersion")
    add("pitestRuntime", "org.pitest:pitest-entry:$pitestEngineVersion")
    add("pitestRuntime", "org.pitest:pitest-command-line:$pitestEngineVersion")
    add("pitestRuntime", "org.pitest:pitest-junit5-plugin:1.2.3")
}

tasks.register<JavaExec>("pitest") {
    group = "verification"
    description = "变异测试（Pitest，夜间专用，不进 check）：默认 targets=features.tnt + features.rank"
    dependsOn(tasks.named("testClasses"))
    classpath = pitestRuntime
    mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")
    val codeUnderTest = sourceSets.main.get().output
    val testClasses = sourceSets.test.get().output
    val testDeps = configurations.testRuntimeClasspath.get()
    val reportDir = layout.buildDirectory.dir("reports/pitest").get().asFile
    doFirst { reportDir.mkdirs() }
    // 被测代码与测试代码分开给：pitest 从 mutableCodePaths 取"要变异的类"（列表参数，须逐个传，
    // 拼成 `a:b` 的单个字符串会被当成一个不存在的路径 → pre-scan 报 0 mutation units）。
    val mutableArgs = codeUnderTest.files.flatMap { listOf("--mutableCodePaths", it.absolutePath) }
    // 同理：--classPath 也是列表参数，必须逐条传（拼成 `a:b` 会被当成一个不存在的路径，
    // 结果是 pre-scan 找不到任何类 → "0 mutation units"）
    val classPathArgs = (codeUnderTest + testClasses + testDeps).files.flatMap { listOf("--classPath", it.absolutePath) }
    args = mutableArgs + classPathArgs + listOf(
        "--reportDir", reportDir.absolutePath,
        "--projectBase", projectDir.absolutePath,
        "--targetClasses", pitestTargetsSpec,
        "--targetTests", "com.jokerhub.paper.plugin.orzmc.*",
        "--sourceDirs", file("src/main/java").absolutePath,
        "--outputFormats", "HTML,XML,CSV",
        "--timestampedReports", "false",
        "--mutators", pitestMutatorsSpec,
        "--threads", pitestThreadsSpec.toString(),
        "--timeoutConst", "10000",
        "--testPlugin", "junit5",
        "--failWhenNoMutations", "false",
        "--verbose", (findProperty("pitestVerbose") as String?) ?: "false",
    )
    isIgnoreExitValue = true // 变异存活不是构建失败：只产出报告供人工/夜间判读
    doLast {
        val csv = File(reportDir, "mutations.csv")
        if (!csv.exists()) {
            logger.lifecycle("Pitest 未产出报告（见上方 PIT 输出）")
            return@doLast
        }
        val rows = csv.readLines().drop(1)
        val killed = rows.count { it.contains(",KILLED,") }
        val survived = rows.count { it.contains(",SURVIVED,") }
        val total = killed + survived
        if (total > 0) {
            logger.lifecycle(
                "Pitest 变异得分 ${"%.1f".format(killed * 100.0 / total)}% " +
                    "（killed=$killed, survived=$survived；存活清单 ${csv.absolutePath}）"
            )
        }
    }
}
