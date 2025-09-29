plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
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

tasks.test {
    useJUnitPlatform()
}