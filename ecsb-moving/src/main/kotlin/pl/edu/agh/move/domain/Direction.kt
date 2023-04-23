package pl.edu.agh.move.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("direction")
enum class Direction(val value: String) {
    @SerialName("none")
    NONE("none"),
    @SerialName("left")
    LEFT("left"),
    @SerialName("up-left")
    UP_LEFT("up-left"),
    @SerialName("up")
    UP("up"),
    @SerialName("up-right")
    UP_RIGHT("up-right"),
    @SerialName("right")
    RIGHT("right"),
    @SerialName("down-right")
    DOWN_RIGHT("down-right"),
    @SerialName("down")
    DOWN("down"),
    @SerialName("down-left")
    DOWN_LEFT("down-left");
}