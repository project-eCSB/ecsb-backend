application {
    mainClass.set("pl.edu.agh.chat.ChatApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
