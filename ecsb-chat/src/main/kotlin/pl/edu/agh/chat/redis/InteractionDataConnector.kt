package pl.edu.agh.chat.redis

import pl.edu.agh.chat.domain.InteractionDto
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.LoggerDelegate

class InteractionDataConnector(private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionDto>) {
    private val logger by LoggerDelegate()
    suspend fun changeStatusData(
        sessionId: GameSessionId,
        senderId: PlayerId,
        interaction: MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage
    ) =
        when (interaction) {
            is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeStartAckMessage -> {
                logger.info("Inserting trade in progress state for $senderId and ${interaction.receiverId}")
                setInteractionData(
                    sessionId,
                    interaction.receiverId,
                    InteractionDto(InteractionStatus.TRADE_IN_PROGRESS, senderId)
                )
                setInteractionData(
                    sessionId,
                    senderId,
                    InteractionDto(InteractionStatus.TRADE_IN_PROGRESS, interaction.receiverId)
                )
            }

            is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage -> {
                logger.info("Canceling trade for $senderId and ${interaction.receiverId}")
                removeInteractionData(sessionId, interaction.receiverId)
                removeInteractionData(sessionId, senderId)
            }

            is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeFinishMessage -> {
                logger.info("Finishing trade for $senderId and ${interaction.receiverId}")
                removeInteractionData(sessionId, interaction.receiverId)
                removeInteractionData(sessionId, senderId)
            }
        }

    suspend fun findOne(gameSessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.findOne(gameSessionId, playerId)

    suspend fun removeInteractionData(sessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.removeElement(sessionId, playerId)

    private suspend fun setInteractionData(
        sessionId: GameSessionId,
        playerId: PlayerId,
        interactionStatus: InteractionDto
    ) =
        redisHashMapConnector.changeData(sessionId, playerId, interactionStatus)
}
