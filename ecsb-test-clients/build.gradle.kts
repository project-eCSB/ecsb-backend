val ktorVersion: String by rootProject

application {
    mainClass.set("pl.edu.agh.clients.TestClientsApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
    implementation(project(mapOf("path" to ":ecsb-game-init")))
    implementation(project(mapOf("path" to ":ecsb-moving")))
    implementation(project(mapOf("path" to ":ecsb-chat")))
    implementation("io.ktor:ktor-client-java-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")
}
