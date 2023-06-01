package pl.edu.agh.tiled.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class PropertiesData {
    @Serializable
    @SerialName("bool")
    data class BooleanProperty(val name: String, val value: Boolean) : PropertiesData()

    @Serializable
    @SerialName("string")
    data class StringProperty(val name: String, val value: String) : PropertiesData()
}

@Serializable
data class Tile(val id: Long, val properties: List<PropertiesData>)
