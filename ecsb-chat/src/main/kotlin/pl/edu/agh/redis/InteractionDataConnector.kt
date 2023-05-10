package pl.edu.agh.redis

import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.LoggerDelegate

class InteractionDataConnector(private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionStatus>) {
    private val logger by LoggerDelegate()
    suspend fun changeStatusData(
        sessionId: GameSessionId,
        senderId: PlayerId,
        interaction: MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage
    ) =
        when (interaction) {
            is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeStartAckMessage -> {
                logger.info("Inserting trade in progress state for $senderId and ${interaction.receiverId}")
                setMovementData(sessionId, interaction.receiverId, InteractionStatus.TRADE_IN_PROGRESS)
                setMovementData(sessionId, senderId, InteractionStatus.TRADE_IN_PROGRESS)
            }

            is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage -> {
                logger.info("Canceling trade for $senderId and ${interaction.receiverId}")
                removeMovementData(sessionId, interaction.receiverId)
                removeMovementData(sessionId, senderId)
            }

            is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeFinishMessage -> {
                logger.info("Finishing trade for $senderId and ${interaction.receiverId}")
                removeMovementData(sessionId, interaction.receiverId)
                removeMovementData(sessionId, senderId)
            }
        }

    suspend fun findOne(gameSessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.findOne(gameSessionId, playerId)

    private suspend fun removeMovementData(sessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.removeElement(sessionId, playerId)

    private suspend fun setMovementData(
        sessionId: GameSessionId,
        playerId: PlayerId,
        interactionStatus: InteractionStatus
    ) =
        redisHashMapConnector.changeData(sessionId, playerId, interactionStatus)
}
