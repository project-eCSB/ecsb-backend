package pl.edu.agh.logs.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonNegInt

@Serializable
sealed interface LogsMessage {

    @Serializable
    data class TravelChange(val travelName: TravelName) : LogsMessage

    @Serializable
    data class WorkshopChoosingChange(val amount: NonNegInt) : LogsMessage

    @Serializable
    data class UserClickedOn(val name: PlayerId) : LogsMessage

    @Serializable
    data class UserJoinedLobby(val name: PlayerId) : LogsMessage

    @Serializable
    data class UserLeftLobby(val name: PlayerId) : LogsMessage
}
