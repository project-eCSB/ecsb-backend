package pl.edu.agh.travel.service

import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.logs.domain.LogsMessage
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.whenA

interface TravelChoosingService {
    suspend fun setInTravel(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun changeTravelDestination(gameSessionId: GameSessionId, playerId: PlayerId, travelName: TravelName)
}

class TravelChoosingServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val logsProducer: InteractionProducer<LogsMessage>
) : TravelChoosingService {
    private val logger by LoggerDelegate()

    override suspend fun setInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.setInteractionData(
            gameSessionId,
            playerId,
            InteractionStatus.TRAVEL_BUSY
        ).whenA({
            logger.error("$playerId in $gameSessionId is already busy, when trying to open travel dialog")
        }) {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStart
            )
        }
    }

    override suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStop
        )
    }

    override suspend fun changeTravelDestination(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ) {
        logsProducer.sendMessage(
            gameSessionId,
            playerId,
            LogsMessage.TravelChange(travelName)
        )
    }
}
