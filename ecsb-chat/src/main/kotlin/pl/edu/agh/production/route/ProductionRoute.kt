package pl.edu.agh.production.route

import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.production.domain.WorkshopInternalMessages
import pl.edu.agh.production.service.ProductionChoosingService

class ProductionRoute(
    private val productionChoosingService: ProductionChoosingService,
    private val interactionProducer: InteractionProducer<WorkshopInternalMessages>
) {

    suspend fun handleWorkshopMessage(
        webSocketUserParams: WebSocketUserParams,
        message: ChatMessageADT.UserInputMessage.WorkshopMessages
    ) {
        val (_, playerId, gameSessionId) = webSocketUserParams
        when (message) {
            ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopChoosingStart ->
                productionChoosingService.setInWorkshop(gameSessionId, playerId)

            ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopChoosingStop ->
                productionChoosingService.removeInWorkshop(gameSessionId, playerId)

            is ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopChoosingChange ->
                productionChoosingService.changeSelectedValues(gameSessionId, playerId, message.amount)

            is ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopStart -> interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                WorkshopInternalMessages.WorkshopStart(message.amount)
            )
        }
    }
}
