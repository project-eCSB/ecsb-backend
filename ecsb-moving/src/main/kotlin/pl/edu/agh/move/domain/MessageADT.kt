package pl.edu.agh.move.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MessageADT {
    @Serializable
    @SerialName("player_moved")
    data class PlayerMoved(val id: String, val x: Int, val y: Int): MessageADT()

    @Serializable
    @SerialName("player_added")
    data class PlayerAdded(val id: String, val x: Int, val y: Int): MessageADT()

    @Serializable
    @SerialName("player_remove")
    data class PlayerRemove(val id: String): MessageADT()
}
