package pl.edu.agh.move.service

import com.rabbitmq.client.Channel
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.domain.BetterMessage
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer.Companion.MOVEMENT_MESSAGES_EXCHANGE
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.utils.ExchangeType
import java.time.LocalDateTime

class MovementCallback(sessionStorage: SessionStorage<WebSocketSession>) :
    MessagePasser<BetterMessage<MessageADT>>(sessionStorage, BetterMessage.serializer(MessageADT.serializer())),
    InteractionConsumer<MessageADT> {

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: MessageADT
    ) {
        when (message) {
            is MessageADT.OutputMessage.PlayerMoved -> broadcast(
                gameSessionId,
                senderId,
                BetterMessage(gameSessionId, senderId, message)
            )

            is MessageADT.OutputMessage.PlayersSync -> unicast(
                gameSessionId,
                senderId,
                senderId,
                BetterMessage(gameSessionId, senderId, message)
            )

            is MessageADT.SystemInputMessage.PlayerAdded -> broadcast(
                gameSessionId,
                senderId,
                BetterMessage(gameSessionId, senderId, message)
            )

            is MessageADT.SystemInputMessage.PlayerRemove -> broadcast(
                gameSessionId,
                senderId,
                BetterMessage(gameSessionId, senderId, message)
            )

            is MessageADT.UserInputMessage -> logger.error("Message $message should not be sent here")
        }
    }

    override val tSerializer: KSerializer<MessageADT> = MessageADT.serializer()

    override fun consumeQueueName(hostTag: String): String = "movement-$hostTag"

    override fun exchangeName(): String = MOVEMENT_MESSAGES_EXCHANGE

    override fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), ExchangeType.FANOUT.value)
        channel.queueDeclare(queueName, true, false, false, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }
}
