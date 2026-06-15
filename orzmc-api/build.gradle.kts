plugins {
    id("java-library")
    id("maven-publish")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("plugin_jdk_min_version") as String))
}

repositories {
    mavenCentral()
}

dependencies {
    // Pure Java only — no Bukkit/Paper dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
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
            version = project.property("orzmc_version") as String
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
}
