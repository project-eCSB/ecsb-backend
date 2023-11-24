package pl.edu.agh.timer

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TimeMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.time.dao.PlayerTimeTokenDao
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import java.time.LocalDateTime

class TimerService(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>
) : InteractionConsumer<TimeInternalMessages> {
    private val logger by LoggerDelegate()
    override val tSerializer: KSerializer<TimeInternalMessages> = TimeInternalMessages.serializer()
    override fun consumeQueueName(hostTag: String): String = "time-in-$hostTag"
    override fun exchangeName(): String = InteractionProducer.TIME_MESSAGES_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.FANOUT
    override fun autoDelete(): Boolean = true

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: TimeInternalMessages
    ) {
        logger.info("Got message from $gameSessionId $senderId sent at $sentAt ($message)")
        when (message) {
            is TimeInternalMessages.GameTimeSyncMessage -> sendSynchronizationData(gameSessionId, senderId)
        }.onLeft {
            logger.warn("WARNING: $it, GAME: ${GameSessionId.toName(gameSessionId)}, SENDER: ${senderId.value}, SENT AT: $sentAt, SOURCE: $message")
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(it, senderId)
            )
        }
    }

    private suspend fun sendSynchronizationData(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Either<String, Unit> =
        either {
            val message = Transactor.dbQuery {
                GameSessionDao.getGameSessionLeftTime(gameSessionId)
                    .toEither { "Error occurred retrieving game time left for session $gameSessionId" }
                    .flatMap { timeLeft ->
                        PlayerTimeTokenDao.getPlayerTokens(gameSessionId, playerId)
                            .toEither { "Error occurred retrieving time tokens for player $playerId in session $gameSessionId" }
                            .map { TimeMessages.TimeSystemOutputMessage.GameTimeSyncResponse(playerId, timeLeft, it) }
                    }
            }.bind()
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                message
            )
        }
}
