application {
    mainClass.set("pl.edu.agh.chat.ApplicationKt")
}

dependencies {
    implementation(project(mapOf("path" to ":ecsb-backend-main")))
}
