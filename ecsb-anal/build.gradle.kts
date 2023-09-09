application {
    mainClass.set("pl.edu.agh.analytics.AnalyticsApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
