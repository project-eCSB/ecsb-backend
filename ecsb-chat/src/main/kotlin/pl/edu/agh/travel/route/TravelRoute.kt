package pl.edu.agh.travel.route

import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.travel.service.TravelChoosingService

class TravelRoute(private val travelChoosingService: TravelChoosingService) {

    suspend fun handleTravelChoosing(
        webSocketUserParams: WebSocketUserParams,
        message: ChatMessageADT.UserInputMessage.TravelChoosing
    ) {
        val (_, playerId, gameSessionId) = webSocketUserParams
        when (message) {
            ChatMessageADT.UserInputMessage.TravelChoosing.TravelChoosingStart -> {
                travelChoosingService.setInTravel(gameSessionId, playerId)
            }

            ChatMessageADT.UserInputMessage.TravelChoosing.TravelChoosingStop -> {
                travelChoosingService.removeInTravel(gameSessionId, playerId)
            }

            is ChatMessageADT.UserInputMessage.TravelChoosing.TravelChange -> {
                travelChoosingService.changeTravelDestination(gameSessionId, playerId, message.travelName)
            }
        }
    }
}
