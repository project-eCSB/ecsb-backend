package pl.edu.agh.move.service

import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer.Companion.MOVEMENT_MESSAGES_EXCHANGE
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.move.domain.MoveMessage
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime

class MovementCallback(private val messagePasser: MessagePasser<MoveMessage>) : InteractionConsumer<MoveMessage> {
    private val logger by LoggerDelegate()
    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: MoveMessage
    ) {
        when (message.message) {
            is MessageADT.OutputMessage.PlayerMoved -> messagePasser.broadcast(gameSessionId, senderId, message)
            is MessageADT.OutputMessage.PlayersSync -> messagePasser.unicast(gameSessionId, senderId, senderId, message)
            is MessageADT.SystemInputMessage.PlayerAdded -> messagePasser.broadcast(gameSessionId, senderId, message)
            is MessageADT.SystemInputMessage.PlayerRemove -> messagePasser.broadcast(gameSessionId, senderId, message)
            is MessageADT.UserInputMessage -> logger.error("Message $message should not be sent here")
        }
    }

    override val tSerializer: KSerializer<MoveMessage> = MoveMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "movement-$hostTag"

    override fun exchangeName(): String = MOVEMENT_MESSAGES_EXCHANGE

    override fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), ExchangeType.FANOUT.value)
        channel.queueDeclare(queueName, true, false, false, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }
}
