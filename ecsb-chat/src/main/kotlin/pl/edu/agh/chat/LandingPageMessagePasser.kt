package pl.edu.agh.chat

import com.rabbitmq.client.Channel
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.interaction.domain.BetterMessage
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.ExchangeType
import java.time.LocalDateTime

class LandingPageMessagePasser(
    sessionStorage: SessionStorage<WebSocketSession>,
) : MessagePasser<BetterMessage<LandingPageMessage>>(
    sessionStorage,
    BetterMessage.serializer(LandingPageMessage.serializer())
),
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
            BetterMessage(gameSessionId, senderId, message, sentAt)
        )
    }

    override val tSerializer: KSerializer<LandingPageMessage> =
        LandingPageMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "landing-page-queue-$hostTag"
    override fun exchangeName(): String = InteractionProducer.LANDING_PAGE_MESSAGES_EXCHANGE
    override fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), ExchangeType.FANOUT.value)
        channel.queueDeclare(queueName, true, false, false, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }

}
