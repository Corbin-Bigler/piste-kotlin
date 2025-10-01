plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.2.0"
    `java-library`
}

group = "com.thysmesi"
version = "0.1"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    val kotlinxCoroutineVersion = "1.10.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("com.github.Corbin-Bigler:logger-kotlin:0.0.2")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutineVersion")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}