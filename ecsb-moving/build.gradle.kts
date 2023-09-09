application {
    mainClass.set("pl.edu.agh.move.MovingApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
