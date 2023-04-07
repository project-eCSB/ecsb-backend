package pl.edu.agh.move.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.PlayerId

@Serializable
sealed class MessageADT {
    @Serializable
    sealed class UserInputMessage : MessageADT() {
        @Serializable
        @SerialName("move")
        data class Move(val coords: Coordinates) : UserInputMessage()

        @Serializable
        @SerialName("sync_request")
        data class SyncRequest(val dummy: String? = null) : UserInputMessage()
    }

    @Serializable
    sealed class SystemInputMessage : MessageADT() {
        @Serializable
        @SerialName("player_added")
        data class PlayerAdded(val id: PlayerId, val coords: Coordinates) : SystemInputMessage()

        @Serializable
        @SerialName("player_remove")
        data class PlayerRemove(val id: PlayerId) : SystemInputMessage()
    }

    @Serializable
    sealed class OutputMessage : MessageADT() {
        @Serializable
        @SerialName("player_syncing")
        data class PlayersSync(val players: List<PlayerPosition>) : OutputMessage()

        @Serializable
        @SerialName("player_moved")
        data class PlayerMoved(val id: PlayerId, val coords: Coordinates) : OutputMessage()
    }
}
