import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.serialization") version "1.7.22"
    id("com.vanniktech.maven.publish") version "0.25.3"
}

group = "io.github.boomkartoffel"
version = "0.1.0-alpha2"

//val ktorVersion = "3.2.0"
val ktorVersion = "2.1.3"
val jacksonVersion = "2.14.3"
val kotlinxSerializationVersion = "1.4.1"
val junitJupiterVersion = "5.13.1"
val kotestVersion = "5.5.5"
val slf4jVersion = "2.0.17"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:${jacksonVersion}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${jacksonVersion}")

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
    jvmToolchain(11)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(11)) }
}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    compilerOptions {
//        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) // classfile target
//        freeCompilerArgs.add("-Xjdk-release=11")
//    }
//}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
        // Keep Kotlin from linking against APIs newer than 11 (works in 1.7.x)
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjdk-release=11")
    }
}


// (Optional, harmless in Kotlin-only projects)
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

extensions.configure<MavenPublishBaseExtension> {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("io.github.boomkartoffel", "potato-cannon", version.toString())
    pom {
        name.set("PotatoCannon")
        description.set("A lightweight, expressive HTTP testing library for Java and Kotlin applications")
        url.set("https://github.com/boomkartoffel/PotatoCannon")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("boomkartoffel")
                name.set("Claus Hinrich Hermanussen")
            }
        }
        scm {
            url.set("https://github.com/boomkartoffel/PotatoCannon")
            connection.set("scm:git:https://github.com/boomkartoffel/PotatoCannon.git")
            developerConnection.set("scm:git:ssh://git@github.com/boomkartoffel/PotatoCannon.git")
        }
        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/boomkartoffel/PotatoCannon/issues")
        }
        inceptionYear.set("2025")
    }
}