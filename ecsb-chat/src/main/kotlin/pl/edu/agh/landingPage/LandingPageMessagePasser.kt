package pl.edu.agh.landingPage

import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.interaction.domain.Message
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.utils.ExchangeType
import java.time.LocalDateTime

class LandingPageMessagePasser(sessionStorage: SessionStorage<WebSocketSession>) :
    MessagePasser<Message<LandingPageMessage>>(sessionStorage, Message.serializer(LandingPageMessage.serializer())),
    InteractionConsumer<LandingPageMessage> {
    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: LandingPageMessage
    ) {
        logger.info("Received message $message from $gameSessionId $senderId at $sentAt")
        broadcast(
            gameSessionId,
            PlayerIdConst.ECSB_CHAT_PLAYER_ID,
            Message(senderId, message, sentAt)
        )
    }

    override val tSerializer: KSerializer<LandingPageMessage> = LandingPageMessage.serializer()
    override fun consumeQueueName(hostTag: String): String = "landing-page-queue-$hostTag"
    override fun exchangeName(): String = InteractionProducer.LANDING_PAGE_MESSAGES_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.FANOUT
    override fun autoDelete(): Boolean = false
}
