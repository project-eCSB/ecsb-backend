application {
    mainClass.set("pl.edu.agh.ApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
