plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
}
application {
    mainClass.set("pl.edu.agh.chat.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
