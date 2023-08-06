application {
    mainClass.set("pl.edu.agh.analytics.ApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
