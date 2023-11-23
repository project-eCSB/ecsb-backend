package pl.edu.agh.move.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.moving.domain.Coordinates
import pl.edu.agh.moving.domain.Direction
import pl.edu.agh.moving.domain.PlayerStatus

@Serializable
sealed class MoveMessageADT {
    @Serializable
    sealed class UserInputMoveMessage : MoveMessageADT() {
        @Serializable
        @SerialName("move")
        data class Move(val coords: Coordinates, val direction: Direction) : UserInputMoveMessage()

        @Serializable
        @SerialName("sync_request")
        data class SyncRequest(val dummy: String? = null) : UserInputMoveMessage()
    }

    @Serializable
    sealed class SystemInputMoveMessage : MoveMessageADT() {
        @Serializable
        @SerialName("player_added")
        data class PlayerAdded(
            val id: PlayerId,
            val coords: Coordinates,
            val direction: Direction,
            val className: GameClassName
        ) :
            SystemInputMoveMessage() {
            companion object {
                fun fromPlayerStatus(playerStatus: PlayerStatus): PlayerAdded {
                    return PlayerAdded(
                        playerStatus.playerId,
                        playerStatus.coords,
                        playerStatus.direction,
                        playerStatus.className
                    )
                }
            }
        }

        @Serializable
        @SerialName("player_remove")
        data class PlayerRemove(val id: PlayerId) : SystemInputMoveMessage()
    }

    @Serializable
    sealed class OutputMoveMessage : MoveMessageADT() {
        @Serializable
        @SerialName("player_syncing")
        data class PlayersSync(val players: List<PlayerPositionWithClass>) : OutputMoveMessage()

        @Serializable
        @SerialName("player_moved")
        data class PlayerMoved(val id: PlayerId, val coords: Coordinates, val direction: Direction) : OutputMoveMessage()
    }
}
