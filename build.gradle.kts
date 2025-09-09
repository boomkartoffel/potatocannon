import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import org.gradle.api.publish.maven.MavenPublication


plugins {
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.serialization") version "1.7.22"
    id("com.vanniktech.maven.publish") version "0.25.3"
    id("org.jetbrains.dokka") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"

}

group = "io.github.boomkartoffel"
version = "0.1.0-alpha2"

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
//    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
//    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
//    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:${jacksonVersion}")
//    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${jacksonVersion}")

    // ---- Jackson via BOM (no versions on modules) ----
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

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

/* -------- Shadow configuration: make shaded JAR the default artifact -------- */
tasks.named<ShadowJar>("shadowJar") {
    // Publish shaded jar as the main artifact (no classifier)
    archiveClassifier.set("")

    // Relocate Jackson so it cannot clash with the app’s Jackson
    relocate("com.fasterxml.jackson", "io.github.boomkartoffel.shaded.com.fasterxml.jackson")

    // Merge META-INF/services so jackson-module-kotlin is auto-discovered
    mergeServiceFiles()

    // avoid signature warnings
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

/*
 * Wire Shadow into the publications that the Vanniktech plugin creates.
 * This swaps the published artifact(s) to use the shaded jar, without needing a 'shadow' component.
 */
afterEvaluate {
    extensions.getByType(PublishingExtension::class.java).publications
        .withType(MavenPublication::class.java)
        .configureEach {
            extensions.getByType(ShadowExtension::class.java).component(this)
        }
}

// Ensure the shaded jar is built by default and the plain jar isn't published by accident.
tasks.named("build") { dependsOn(tasks.named("shadowJar")) }
tasks.named<Jar>("jar") { enabled = false }

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


tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
        // includeNonPublic.set(true)  // (v1) if you want internal/private
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(uri("https://github.com/your-org/potato-cannon/tree/main/src/main/kotlin").toURL())
            remoteLineSuffix.set("#L")
        }
    }
}