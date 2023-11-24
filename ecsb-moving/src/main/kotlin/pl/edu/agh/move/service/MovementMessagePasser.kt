package pl.edu.agh.move.service

import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.interaction.domain.Message
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer.Companion.MOVEMENT_MESSAGES_EXCHANGE
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.MoveMessageADT
import pl.edu.agh.utils.ExchangeType
import java.time.LocalDateTime

class MovementMessagePasser(sessionStorage: SessionStorage<WebSocketSession>) :
    MessagePasser<Message<MoveMessageADT>>(sessionStorage, Message.serializer(MoveMessageADT.serializer())),
    InteractionConsumer<MoveMessageADT> {
    override val tSerializer: KSerializer<MoveMessageADT> = MoveMessageADT.serializer()
    override fun consumeQueueName(hostTag: String): String = "movement-$hostTag"
    override fun exchangeName(): String = MOVEMENT_MESSAGES_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.FANOUT
    override fun autoDelete(): Boolean = false

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: MoveMessageADT
    ) {
        when (message) {
            is MoveMessageADT.OutputMoveMessage.PlayerMoved -> broadcast(
                gameSessionId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is MoveMessageADT.OutputMoveMessage.PlayersSync -> unicast(
                gameSessionId,
                PlayerIdConst.ECSB_MOVING_PLAYER_ID,
                senderId,
                Message(senderId, message, sentAt)
            )

            is MoveMessageADT.SystemInputMoveMessage.PlayerAdded -> broadcast(
                gameSessionId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is MoveMessageADT.SystemInputMoveMessage.PlayerRemove -> broadcast(
                gameSessionId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is MoveMessageADT.UserInputMoveMessage -> logger.error("Message $message should not be sent here")
        }
    }
}
