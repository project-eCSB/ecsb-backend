package pl.edu.agh.analytics

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import pl.edu.agh.analytics.service.AnalyticsService
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime

class AnalyticsConsumer(private val analyticsService: AnalyticsService) : InteractionConsumer<JsonElement> {
    override val tSerializer: KSerializer<JsonElement> = JsonElement.serializer()
    override fun consumeQueueName(hostTag: String): String = "analytics-queue"
    override fun exchangeName(): String = InteractionProducer.MAIN_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.FANOUT
    override fun autoDelete(): Boolean = false

    private val logger by LoggerDelegate()

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: JsonElement
    ) {
        logger.info("Message arrived from $gameSessionId ($senderId) at $sentAt, $message")
        val messageStr = Json.encodeToString(JsonElement.serializer(), message)
        analyticsService.saveLog(gameSessionId, senderId, sentAt, messageStr)
    }
}
