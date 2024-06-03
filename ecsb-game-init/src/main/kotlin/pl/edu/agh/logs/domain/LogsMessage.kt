package pl.edu.agh.logs.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

@Serializable
sealed interface LogsMessage {

    @Serializable
    @SerialName("lobby/join")
    data class UserJoinedLobby(val name: PlayerId) : LogsMessage

    @Serializable
    @SerialName("lobby/left")
    data class UserLeftLobby(val name: PlayerId) : LogsMessage
}
