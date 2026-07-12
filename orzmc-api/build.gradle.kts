import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("java-library")
    id("maven-publish")
    id("jacoco")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("plugin_jdk_min_version") as String))
}

repositories {
    mavenCentral()
}

dependencies {
    // Pure Java only — no Bukkit/Paper dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.2")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("orzmcApi") {
            groupId = project.property("orzmc_group") as String
            artifactId = "orzmc-api"
            version = rootProject.version.toString()
            from(components["java"])
            pom {
                name.set("OrzMC API")
                description.set("Core ports and models for the OrzMC PaperMC plugin")
                url.set("https://github.com/OrzGeeker/OrzMC")
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy("jacocoTestReport")
}

tasks.withType<JacocoReport>().configureEach {
    dependsOn("test")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// orzmc-api 覆盖率验证门禁（纯接口模块，100% 覆盖）
tasks.withType<JacocoCoverageVerification>().configureEach {
    dependsOn("test")
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal.valueOf(1.00)
            }
        }
        rule {
            limit {
                counter = "LINE"
                minimum = BigDecimal.valueOf(1.00)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
