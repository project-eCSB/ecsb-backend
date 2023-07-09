application {
    mainClass.set("pl.edu.agh.coop.GameEngineApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
    implementation(project(mapOf("path" to ":ecsb-chat")))
}
