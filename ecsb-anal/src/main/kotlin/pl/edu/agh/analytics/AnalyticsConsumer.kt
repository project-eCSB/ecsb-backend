package pl.edu.agh.analytics

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import pl.edu.agh.analytics.service.AnalyticsService
import pl.edu.agh.analytics.service.FeedModelRequest
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime

class AnalyticsConsumer(
    private val analyticsService: AnalyticsService,
    private val decisionModelConfig: DecisionModelConfig
) : InteractionConsumer<JsonElement> {
    override val tSerializer: KSerializer<JsonElement> = JsonElement.serializer()
    override fun consumeQueueName(hostTag: String): String = "analytics-queue"
    override fun exchangeName(): String = InteractionProducer.MAIN_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.FANOUT
    override fun autoDelete(): Boolean = false

    private val logger by LoggerDelegate()

    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
    }

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: JsonElement
    ) {
        logger.info("Message arrived from $gameSessionId ($senderId) at $sentAt, $message")
        val messageStr = Json.encodeToString(JsonElement.serializer(), message)
        analyticsService.saveLog(gameSessionId, senderId, sentAt, messageStr)

        if (decisionModelConfig.enable && messageStr.contains("time/end")) {
            val areLogsSent = analyticsService.areLogsSend(gameSessionId)
            if (!areLogsSent) {
                analyticsService.sendLogs(gameSessionId).onSome {
                    val request = FeedModelRequest(gameSessionId, it)
                    val statusCode =
                        client.post("http://${decisionModelConfig.host}:${decisionModelConfig.port}/${decisionModelConfig.postData}") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                        }.status
                    logger.info("Sending logs from session $gameSessionId ended with code $statusCode")
                }
            }
        }
    }
}
