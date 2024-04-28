val ktorVersion: String by rootProject

application {
    mainClass.set("pl.edu.agh.move.MovingApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
}
