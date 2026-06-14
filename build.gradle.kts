import org.gradle.kotlin.dsl.support.serviceOf
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.attributes.java.TargetJvmVersion

buildscript {
    repositories {
        // 中国大陆备用镜像站：阿里云
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        // 官方Maven中心仓
        mavenCentral()
    }
    dependencies {
        classpath("org.yaml:snakeyaml:2.4")
    }
}

val pluginYaml = Yaml().load(File("src/main/resources/plugin.yml").inputStream()) as Map<String, Any>
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
    // 中国大陆备用镜像站：阿里云
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/public")
    // 官方Maven中心仓
    mavenCentral()
    
}
dependencies {
    // orc-mc-api 子模块（纯 Java 端口与模型）
    implementation(project(":orzmc-api"))

    compileOnly("io.papermc.paper:paper-api:${pluginYaml["api-version"]}-R0.1-SNAPSHOT")
    // WebSocket Client For NapCat QQBot
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    // Java Discord API
    implementation("net.dv8tion:JDA:6.2.1") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    // Minecraft World Backup Lib
    implementation("io.github.wangzhizhou:backup-core:0.1.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    testImplementation("io.papermc.paper:paper-api:${pluginYaml["api-version"]}-R0.1-SNAPSHOT")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
}
integrationTestSourceSet.compileClasspath += sourceSets.main.get().output
integrationTestSourceSet.runtimeClasspath += integrationTestSourceSet.output + integrationTestSourceSet.compileClasspath

configurations.getByName("integrationTestCompileClasspath")
    .attributes
    .attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
configurations.getByName("integrationTestRuntimeClasspath")
    .attributes
    .attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)

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
    add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter:5.10.1")
    add("integrationTestImplementation", "io.papermc.paper:paper-api:$integrationPaperVersion-R0.1-SNAPSHOT")
    add("integrationTestImplementation", "org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.101.0")
    add("integrationTestImplementation", "com.squareup.okhttp3:mockwebserver:4.12.0")
    add("integrationTestImplementation", "org.mockito:mockito-core:5.11.0")
    add("integrationTestImplementation", "org.mockito:mockito-junit-jupiter:5.11.0")
    add("integrationTestRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.10.1")
    add("integrationTestRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.1")
}

// 项目编译时插件添加
plugins {
    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "8.3.8"
    // 工程内直接调试服务端插件：https://docs.papermc.io/paper/dev/debugging#using-direct-debugging
    id("xyz.jpenilla.run-paper") version "3.0.2"
    // 自动发布版本配置文档：https://docs.papermc.io/misc/hangar-publishing/
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    id("com.diffplug.spotless") version "6.25.0"
}

// 代码格式化
spotless {
    java {
        palantirJavaFormat()
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

val suffixedVersion: String = if (isRelease) {
    // Tag push → Hangar Release: {version}
    versionString
} else {
    // Branch push (main) → Hangar Snapshot: {version}.{run_number}
    "${versionString}.${githubRunNumber}"
}

val archiveClassifierSuffix: String = if (isPrBuild) {
    // PR 构件文件名: {version}-dev_{timestamp}
    "dev_${timestampString}"
} else if (isRelease) {
    // Release 构件: 无后缀
    ""
} else {
    // Snapshot 构件: 当前行为
    githubRunNumber ?: ""
}

// Use the commit description for the changelog
val changelogContent: String = latestCommitMessage()

hangarPublish {
    publications.register("plugin") {
        version = suffixedVersion
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
    named<JavaCompile>("compileIntegrationTestJava") {
        val java21 = serviceOf<JavaToolchainService>().compilerFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        javaCompiler.set(java21)
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
        val java21 = serviceOf<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        javaLauncher.set(java21)
        dependsOn(agreeEula)
    }
    jar {
        archiveClassifier.set(archiveClassifierSuffix)
    }
    shadowJar {
        minimize()
        dependsOn("jar")
        archiveClassifier.set(archiveClassifierSuffix)
    }
    build {
        dependsOn("shadowJar")
    }
    withType<Test> {
        useJUnitPlatform()
        jvmArgs("-Xshare:off")
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
        val java21 = serviceOf<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        javaLauncher.set(java21)
    }
}
