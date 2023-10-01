application {
    mainClass.set("pl.edu.agh.timer.TimerApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
    implementation(project(mapOf("path" to ":ecsb-chat")))
}