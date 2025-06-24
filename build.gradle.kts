plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

group = "io.github.boomkartoffel"
version = "1.0-SNAPSHOT"

val ktorVersion = "3.2.0"
val jacksonVersion = "2.19.1"
val kotlinxSerializationVersion = "1.6.0"
val junitJupiterVersion = "5.10.0"
val kotestVersion = "5.8.1"
val slf4jVersion = "2.0.13"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:${jacksonVersion}")

    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    testImplementation("io.ktor:ktor-server-core:$ktorVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")

    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")

    //this is to remove SLF4J(W): No SLF4J providers were found. error message
    testImplementation("org.slf4j:slf4j-nop:$slf4jVersion")
    //this is to remove netty logging errors on shutdown
    testImplementation("org.slf4j:jul-to-slf4j:${slf4jVersion}")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}