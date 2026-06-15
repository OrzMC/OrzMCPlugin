import org.gradle.kotlin.dsl.support.serviceOf
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    // 官方Maven中心仓（放在阿里云镜像之前，避免 CI 被阿里云 502 阻断）
    mavenCentral()
    // 中国大陆备用镜像站：阿里云（仅作 fallback）
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/public")
}
dependencies {
    // orc-mc-api 子模块（纯 Java 端口与模型）
    implementation(project(":orzmc-api"))

    compileOnly("io.papermc.paper:paper-api:${property("plugin_debug_server_version") as String}.build.+")
    // WebSocket Client For NapCat QQBot
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    // Java Discord API
    implementation("net.dv8tion:JDA:6.4.2") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    // Minecraft World Backup Lib
    implementation("io.github.wangzhizhou:backup-core:0.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
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
    add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter:6.1.0")
    add("integrationTestImplementation", "io.papermc.paper:paper-api:$integrationPaperVersion.build.+")
    add("integrationTestImplementation", "org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.2")
    add("integrationTestImplementation", "com.squareup.okhttp3:mockwebserver:5.4.0")
    add("integrationTestImplementation", "org.mockito:mockito-core:5.23.0")
    add("integrationTestImplementation", "org.mockito:mockito-junit-jupiter:5.23.0")
    add("integrationTestRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:6.1.0")
    add("integrationTestRuntimeOnly", "org.junit.platform:junit-platform-launcher:6.1.0")
}

// 项目编译时插件添加
plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    // 工程内直接调试服务端插件：https://docs.papermc.io/paper/dev/debugging#using-direct-debugging
    id("xyz.jpenilla.run-paper") version "3.0.2"
    // 自动发布版本配置文档：https://docs.papermc.io/misc/hangar-publishing/
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    id("com.diffplug.spotless") version "8.6.0"
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
val timestampString: String? = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmSS"))
val versionString: String = version as String
val isRelease: Boolean = (githubRefType == "tag")
val isPrBuild: Boolean = (githubEventName == "pull_request")

val shadowJarVersion: String = if (isPrBuild) {
    "${versionString}-dev-${timestampString}"
} else if (isRelease) {
    // Tag push → Release: {version}
    versionString
} else {
    // Branch push (main) → Snapshot: {version}-snapshot-{run_number}
    "${versionString}-snapshot-${githubRunNumber}"
}

// Use the commit description for the changelog
val changelogContent: String = latestCommitMessage()

hangarPublish {
    publications.register("plugin") {
        version = shadowJarVersion
        channel = if (isRelease) "Release" else "Snapshot"
        changelog = changelogContent
        id = pluginYaml["name"] as String
        apiKey = System.getenv("HANGAR_API_TOKEN")
        platforms {
            paper {
                jar = tasks.shadowJar.flatMap { it.archiveFile }
                platformVersions = (property("plugin_support_paper_versions") as String).split(",").map { it.trim() }
            }
        }
    }
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
        finalizedBy("jacocoTestReport")
    }
    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    named("check") {
        dependsOn("integrationTest")
    }
    register<Test>("integrationTest") {
        description = "Runs integration tests on a mocked Paper server."
        group = "verification"
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(test)
    }
}
