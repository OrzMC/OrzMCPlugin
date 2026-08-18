import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

buildscript {
    repositories {
        // 官方Maven中心仓（仅用于解析 snakeyaml，不用于 Gradle 插件，
        // 插件通过 plugins {} 块中的默认仓库解析，避免阿里云镜像 502 阻断 CI）
        mavenCentral()
    }
    dependencies {
        classpath("org.yaml:snakeyaml:2.6")
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
    implementation("io.github.wangzhizhou:backup-core:0.1.6")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("io.papermc.paper:paper-api:${property("paper_api_version") as String}")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
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
    add("integrationTestImplementation", "org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.115.0")
}

// 项目编译时插件添加
plugins {
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "9.6.1"
    // 工程内直接调试服务端插件：https://docs.papermc.io/paper/dev/debugging#using-direct-debugging
    id("xyz.jpenilla.run-paper") version "3.1.0"
    // 自动发布版本配置文档：https://docs.papermc.io/misc/hangar-publishing/
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    // Modrinth 自动发布：https://github.com/modrinth/minotaur
    id("com.modrinth.minotaur") version "2.9.0"
    id("com.diffplug.spotless") version "8.9.0"
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
    return runCatching { executeGitCommand("log", "-1", "--pretty=%B") }
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

    // foliaSmoke：真实 Folia 无头冒烟（评估文档 D8）。下载与启动拆成两个任务：
    // downloadFoliaJar 有缓存（build/folia-smoke/），foliaSmoke 每次执行都真实启动不跳过。
    // 注意：不能嵌套调用 gradlew 下载/启动（会死锁在项目锁上），故自行解析 Fill API v3 + 直接 java -jar。
    val foliaSmokeJar = layout.buildDirectory.file("folia-smoke/folia-$debugServerVersion.jar")
    val foliaSmokeMarker = layout.buildDirectory.file("folia-smoke/.folia-$debugServerVersion.ok")
    register("downloadFoliaJar") {
        group = "verification"
        description = "下载 Folia 服务端 jar（Paper Fill API v3，SnakeYAML 解析 JSON 响应）"
        inputs.property("foliaVersion", debugServerVersion)
        outputs.file(foliaSmokeJar)
        // 下载成功且 SHA256 通过才写标记文件；中途被杀留下的残缺 jar 因标记缺失会被判 out-of-date 重下
        outputs.file(foliaSmokeMarker)
        doLast {
            val jarFile = foliaSmokeJar.get().asFile
            val apiUrl = "https://fill.papermc.io/v3/projects/folia/versions/$debugServerVersion/builds/latest"
            // Fill API 返回 JSON，而 JSON ⊂ YAML，直接复用 buildscript 已有的 SnakeYAML 解析
            val response = URI(apiUrl).toURL().openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val data = Yaml().load(response) as Map<*, *>
            val serverDefault = (data["downloads"] as Map<*, *>)["server:default"] as Map<*, *>
            val downloadUrl = serverDefault["url"] as String
            val expectedSha = (serverDefault["checksums"] as Map<*, *>)["sha256"] as String
            jarFile.parentFile.mkdirs()
            val conn = URI(downloadUrl).toURL().openConnection()
            conn.setRequestProperty("User-Agent", "OrzMC/folia-smoke")
            conn.connect()
            val digest = MessageDigest.getInstance("SHA-256")
            conn.getInputStream().use { input ->
                jarFile.outputStream().use { out ->
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
                jarFile.delete()
                throw GradleException("Folia jar SHA256 校验失败: expected=$expectedSha actual=$actualSha")
            }
            foliaSmokeMarker.get().asFile.writeText("sha256=$actualSha\n")
            logger.lifecycle("已下载 Folia $debugServerVersion → ${jarFile.absolutePath}")
        }
    }
    register("foliaSmoke") {
        group = "verification"
        description = "真实 Folia 无头冒烟：启动服务端 → 校验 OrzMC 加载 → stop → 断言干净退出"
        dependsOn("downloadFoliaJar", project.tasks.shadowJar)
        outputs.upToDateWhen { false } // 每次执行都真实启动，不因缓存跳过
        // 配置期解析（避免 doLast 内 Task.project 的执行期弃用告警 / 配置缓存不兼容）
        val runDir = project.layout.projectDirectory.dir("run-folia-smoke").asFile
        val shadowJarFile = project.tasks.shadowJar.get().archiveFile.get().asFile
        val javaBin = project.javaToolchains.launcherFor(project.java.toolchain).get().executablePath.asFile.absolutePath
        doLast {
            val serverJar = foliaSmokeJar.get().asFile
            runDir.mkdirs()
            File(runDir, "eula.txt").writeText("eula=true\n")
            val pluginsDir = File(runDir, "plugins").apply { mkdirs() }
            shadowJarFile.copyTo(File(pluginsDir, "OrzMC.jar"), overwrite = true)
            // OrzMC 默认 deny-list 拦截 stop/reload 等危险命令（安全加固）；冒烟测试需要干净退出，
            // 故在隔离的 run-folia-smoke/ 内放一份空 deny-list 配置（仅影响本次冒烟，不触碰真实配置）
            val orzmcDataDir = File(pluginsDir, "OrzMC").apply { mkdirs() }
            File(orzmcDataDir, "config.yml").writeText("guard:\n  blocked_commands: []\n")

            val logFile = File(runDir, "smoke.log").apply { if (exists()) delete() }
            // 用 Java 25 toolchain 启动（Folia 26.2 最低要求 Java 25），不依赖 Gradle 守护进程 JDK
            val cmd = listOf(
                javaBin, "-Xmx2G", "-Ddisable.watchdog=true",
                "-jar", serverJar.absolutePath,
                "--nogui", "--nojline", "--online-mode=false", "--port", "25580"
            )
            logger.lifecycle("启动 Folia 冒烟服务器: ${cmd.joinToString(" ")}")
            val proc = ProcessBuilder(cmd)
                .directory(runDir)
                .redirectErrorStream(true)
                .redirectOutput(logFile)
                .start()

            val startedMs = waitForLog(logFile, Regex("Done \\(.*\\)"), 240_000)
            if (startedMs < 0) {
                proc.destroyForcibly()
                proc.waitFor(30, TimeUnit.SECONDS)
                throw GradleException("Folia 服务器 240s 内未完成启动。日志尾部:\n${logTail(logFile)}")
            }
            logger.lifecycle("Folia 已启动（${startedMs}ms）。校验 OrzMC 加载...")
            // 插件加载后日志会出现 [OrzMC] 前缀行；plugins 命令响应列表也含 OrzMC
            if (waitForLog(logFile, Regex("OrzMC"), 15_000) < 0) {
                proc.destroyForcibly()
                proc.waitFor(30, TimeUnit.SECONDS)
                throw GradleException("OrzMC 插件未加载。日志尾部:\n${logTail(logFile)}")
            }
            logger.lifecycle("OrzMC 已加载。发送 stop...")
            proc.outputStream.write("stop\n".toByteArray(Charsets.UTF_8))
            proc.outputStream.flush()
            if (!proc.waitFor(90, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                throw GradleException("Folia 在 stop 后 90s 内未退出。日志尾部:\n${logTail(logFile)}")
            }
            if (proc.exitValue() != 0) {
                throw GradleException("Folia 退出码非零: ${proc.exitValue()}。日志尾部:\n${logTail(logFile)}")
            }
            logger.lifecycle("Folia 冒烟通过：干净退出（exit 0）")
        }
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
        useJUnitPlatform()
        jvmArgs("-Xshare:off")
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
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal.valueOf(0.60)
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = BigDecimal.valueOf(0.50)
            }
        }
        rule {
            limit {
                counter = "LINE"
                minimum = BigDecimal.valueOf(0.55)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("integrationTest", "jacocoTestCoverageVerification")
}
