package pl.edu.agh.production.service

import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.logs.domain.LogsMessage
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.whenA

interface ProductionChoosingService {
    suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun changeSelectedValues(gameSessionId: GameSessionId, playerId: PlayerId, amount: NonNegInt)
}

class ProductionChoosingServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val logsProducer: InteractionProducer<LogsMessage>
) : ProductionChoosingService {
    private val logger by LoggerDelegate()

    override suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.setInteractionData(
            gameSessionId,
            playerId,
            InteractionStatus.PRODUCTION_BUSY
        ).whenA({
            logger.error("$playerId in $gameSessionId is already busy, when trying to open production dialog")
        }) {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStart
            )
        }
    }

    override suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStop
        )
    }

    override suspend fun changeSelectedValues(gameSessionId: GameSessionId, playerId: PlayerId, amount: NonNegInt) {
        logsProducer.sendMessage(
            gameSessionId,
            playerId,
            LogsMessage.WorkshopChoosingChange(amount)
        )
    }
}
