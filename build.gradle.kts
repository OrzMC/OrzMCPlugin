import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream

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
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    mavenCentral()
}
dependencies {
    // orc-mc-api 子模块（纯 Java 端口与模型）
    implementation(project(":orzmc-api"))

    compileOnly("io.papermc.paper:paper-api:${property("plugin_debug_server_version") as String}.build.+")
    // WebSocket Client For NapCat QQBot
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    // Java Discord API
    implementation("net.dv8tion:JDA:6.5.0") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    // Minecraft World Backup Lib
    implementation("io.github.wangzhizhou:backup-core:0.1.6")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
    testImplementation("io.papermc.paper:paper-api:${property("plugin_debug_server_version") as String}.build.+")
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
    val integrationPaperVersion = property("plugin_debug_server_version") as String
    add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter:6.1.2")
    add("integrationTestImplementation", "io.papermc.paper:paper-api:$integrationPaperVersion.build.+")
    add("integrationTestImplementation", "org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.114.0")
    add("integrationTestImplementation", "com.squareup.okhttp3:mockwebserver:5.4.0")
    add("integrationTestImplementation", "org.mockito:mockito-core:5.23.0")
    add("integrationTestImplementation", "org.mockito:mockito-junit-jupiter:5.23.0")
    add("integrationTestRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:6.1.2")
    add("integrationTestRuntimeOnly", "org.junit.platform:junit-platform-launcher:6.1.2")
}

// 项目编译时插件添加
plugins {
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "9.5.1"
    // 工程内直接调试服务端插件：https://docs.papermc.io/paper/dev/debugging#using-direct-debugging
    id("xyz.jpenilla.run-paper") version "3.0.2"
    // 自动发布版本配置文档：https://docs.papermc.io/misc/hangar-publishing/
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    // Modrinth 自动发布：https://github.com/modrinth/minotaur
    id("com.modrinth.minotaur") version "2.+"
    id("com.diffplug.spotless") version "8.8.0"
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
    return executeGitCommand("log", "-1", "--pretty=%B")
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

    // 同步 README.md 到 Modrinth 项目主页
    syncBodyFrom.set(project.file("README.md").readText())
}

val debugServerVesion = property("plugin_debug_server_version") as String
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
        minecraftVersion(debugServerVesion)
        // 以离线模式启动服务端
        args("--nojline", "--nogui", "--online-mode=false")
        dependsOn(agreeEula)
    }
    jar {
        enabled = false
    }
    shadowJar {
        minimize()
        archiveClassifier.set(null as String?)
        archiveVersion.set(shadowJarVersion)
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
        finalizedBy("jacocoTestReport")
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
        finalizedBy("jacocoTestReport")
    }
}

// JaCoCo 报告输出（在 tasks {} 块外用 withType 避免 Kotlin DSL 接收者歧义）
tasks.withType<JacocoReport>().configureEach {
    dependsOn("test")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// JaCoCo 覆盖率验证门禁
tasks.withType<JacocoCoverageVerification>().configureEach {
    dependsOn("test")
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
