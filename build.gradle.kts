
val ktor_version: String by rootProject
val kotlin_version: String by rootProject
val logback_version: String by rootProject
val exposed_version: String by rootProject
val postgres_version: String by rootProject
val koin_version: String by rootProject
val koin_ktor: String by rootProject

plugins {
    kotlin("jvm") version "1.8.10" apply false
    id("io.ktor.plugin") version "2.2.3" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10" apply false
}

allprojects {
    group = "pl.edu.agh"
    version = "0.0.1"

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
        kotlinOptions.jvmTarget = "11"
    }

}


subprojects {
    repositories {
        mavenCentral()
    }
    apply {
        plugin("io.ktor.plugin")
        plugin("org.jetbrains.kotlin.plugin.serialization")
    }

    tasks {
        named<Test>("test") {
            useJUnitPlatform()
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        implementation("io.insert-koin:koin-core:$koin_version")
        implementation("ch.qos.logback:logback-classic:$logback_version")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
        testImplementation("io.insert-koin:koin-test-junit5:$koin_version")
        implementation("io.arrow-kt:arrow-core:1.1.2")

        implementation("io.insert-koin:koin-ktor:$koin_ktor")
        implementation("io.insert-koin:koin-logger-slf4j:$koin_ktor")
    }
}
