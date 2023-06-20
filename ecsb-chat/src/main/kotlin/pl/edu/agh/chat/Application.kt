package pl.edu.agh.chat

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.awaitCancellation
import org.koin.ktor.plugin.Koin
import pl.edu.agh.auth.AuthModule.getKoinAuthModule
import pl.edu.agh.auth.service.configureSecurity
import pl.edu.agh.chat.ChatModule.getKoinChatModule
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.route.ChatRoutes.configureChatRoutes
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.interaction.domain.InteractionDto
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionMessagePasser
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.messages.service.simple.SimpleMessagePasser
import pl.edu.agh.production.route.ProductionRoute.Companion.configureProductionRoute
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.travel.route.TravelRoute.Companion.configureTravelRoute
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import java.time.Duration

fun main(): Unit = SuspendApp {
    val chatConfig = ConfigUtils.getConfigOrThrow<ChatConfig>()
    val sessionStorage = SessionStorageImpl()

    resourceScope {
        val redisMovementDataConnector = RedisHashMapConnector.createAsResource(
            chatConfig.redis,
            RedisHashMapConnector.MOVEMENT_DATA_PREFIX,
            GameSessionId::toName,
            PlayerId.serializer(),
            PlayerPosition.serializer()
        ).bind()

        val redisInteractionStatusConnector = RedisHashMapConnector.createAsResource(
            chatConfig.redis,
            RedisHashMapConnector.INTERACTION_DATA_PREFIX,
            GameSessionId::toName,
            PlayerId.serializer(),
            InteractionDto.serializer()
        ).bind()

        DatabaseConnector.initDBAsResource().bind()

        val simpleMessagePasser = SimpleMessagePasser.create(sessionStorage, Message.serializer()).bind()

        val interactionRabbitMessagePasser = InteractionMessagePasser(
            simpleMessagePasser,
            redisMovementDataConnector
        )

        InteractionConsumer.create(
            chatConfig.rabbitConfig,
            interactionRabbitMessagePasser,
            System.getProperty("rabbitHostTag", "develop")
        ).bind()

        val systemInputProducer: InteractionProducer<ChatMessageADT.SystemInputMessage> = InteractionProducer.create(
            chatConfig.rabbitConfig,
            ChatMessageADT.SystemInputMessage.serializer(),
            InteractionProducer.INTERACTION_EXCHANGE
        ).bind()

        val coopMessagesProducer: InteractionProducer<CoopInternalMessages> = InteractionProducer.create(
            chatConfig.rabbitConfig,
            CoopInternalMessages.serializer(),
            InteractionProducer.COOP_MESSAGES_EXCHANGE
        ).bind()

        server(
            Netty,
            host = chatConfig.httpConfig.host,
            port = chatConfig.httpConfig.port,
            preWait = chatConfig.httpConfig.preWait,
            module = chatModule(
                chatConfig,
                sessionStorage,
                simpleMessagePasser,
                redisInteractionStatusConnector,
                systemInputProducer,
                coopMessagesProducer
            )
        )

        awaitCancellation()
    }
}

fun chatModule(
    chatConfig: ChatConfig,
    sessionStorage: SessionStorage<WebSocketSession>,
    messagePasser: MessagePasser<Message>,
    redisInteractionStatusConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionDto>,
    interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>,
    coopMessagesProducer: InteractionProducer<CoopInternalMessages>
): Application.() -> Unit = {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeadersPrefixed("")
        allowNonSimpleContentTypes = true
        anyHost()
    }
    install(Koin) {
        modules(
            getKoinAuthModule(chatConfig.jwt, chatConfig.gameToken),
            getKoinChatModule(
                sessionStorage,
                messagePasser,
                redisInteractionStatusConnector,
                interactionProducer,
                coopMessagesProducer
            )
        )
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    configureSecurity(chatConfig.jwt, chatConfig.gameToken)
    configureChatRoutes(chatConfig.gameToken)
    configureProductionRoute()
    configureTravelRoute()
}
