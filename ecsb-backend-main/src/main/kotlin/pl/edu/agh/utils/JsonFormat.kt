package pl.edu.agh.utils

import kotlinx.serialization.json.Json

object JsonFormat {
    val jsonFormat = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
}