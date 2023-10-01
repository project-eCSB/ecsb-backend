application {
    mainClass.set("pl.edu.agh.move.MovingApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:2.2.3")
}
