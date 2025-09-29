plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
}

val reverseDomain = "com.thysmesi"
group = reverseDomain
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
            groupId = reverseDomain
            artifactId = "piste"
            version = "0.0.1"
        }
    }
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/YOUR_USERNAME/YOUR_REPO")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USER")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}