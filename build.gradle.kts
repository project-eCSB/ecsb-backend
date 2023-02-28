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

}
