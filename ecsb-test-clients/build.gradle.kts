val ktorVersion: String by rootProject

application {
    mainClass.set("pl.edu.agh.clients.TestClientsApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation(project(mapOf("path" to ":ecsb-game-init")))
    implementation("io.ktor:ktor-client-encoding:2.2.3")
    implementation(project(mapOf("path" to ":ecsb-moving")))
    implementation("io.ktor:ktor-client-logging-jvm:2.2.3")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation(project(mapOf("path" to ":ecsb-chat")))
}
