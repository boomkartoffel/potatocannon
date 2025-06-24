plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

group = "io.github.boomkartoffel"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
//    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.eclipse.jetty:jetty-client:12.0.22")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-core:2.3.7")
    testImplementation("io.ktor:ktor-server-netty:2.3.7")
    testImplementation("io.ktor:ktor-server-test-host:2.3.7") // optional
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.19.1")


    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    testImplementation("io.kotest:kotest-runner-junit5:5.8.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.1")
//    remove logger warning
    testImplementation("org.slf4j:slf4j-nop:2.0.13")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}