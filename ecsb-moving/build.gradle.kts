application {
    mainClass.set("pl.edu.agh.move.ApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
