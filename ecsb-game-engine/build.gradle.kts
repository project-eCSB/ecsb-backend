plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
}
application {
    mainClass.set("pl.edu.agh.GameEngineApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
    implementation(project(mapOf("path" to ":ecsb-chat")))
}
