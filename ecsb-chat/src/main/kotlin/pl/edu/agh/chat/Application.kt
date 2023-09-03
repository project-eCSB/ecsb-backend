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
import pl.edu.agh.chat.domain.LogsMessage
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.route.ChatRoutes.configureChatRoutes
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.equipment.route.EquipmentRoute.configureEquipmentRoute
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionMessagePasser
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.messages.service.simple.SimpleMessagePasser
import pl.edu.agh.production.route.ProductionRoute.Companion.configureProductionRoute
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.travel.route.TravelRoute.Companion.configureTravelRoute
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType
import java.time.Duration

fun main(): Unit = SuspendApp {
    val chatConfig = ConfigUtils.getConfigOrThrow<ChatConfig>()
    val sessionStorage = SessionStorageImpl()

    resourceScope {
        val redisMovementDataConnector = RedisJsonConnector.createAsResource(
            RedisJsonConnector.Companion.MovementCreationParams(chatConfig.redis)
        ).bind()

        DatabaseConnector.initDBAsResource().bind()

        val simpleMessagePasser = SimpleMessagePasser.create(sessionStorage, Message.serializer()).bind()

        RabbitMainExchangeSetup.setup(chatConfig.rabbitConfig)

        val interactionRabbitMessagePasser = InteractionMessagePasser(
            simpleMessagePasser,
            redisMovementDataConnector
        )

        InteractionConsumerFactory.create(
            chatConfig.rabbitConfig,
            interactionRabbitMessagePasser,
            System.getProperty("rabbitHostTag", "develop")
        ).bind()

        val systemOutputProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage> =
            InteractionProducer.create(
                chatConfig.rabbitConfig,
                ChatMessageADT.SystemOutputMessage.serializer(),
                InteractionProducer.INTERACTION_EXCHANGE,
                ExchangeType.FANOUT
            ).bind()

        val coopMessagesProducer: InteractionProducer<CoopInternalMessages> =
            InteractionProducer.create(
                chatConfig.rabbitConfig,
                CoopInternalMessages.serializer(),
                InteractionProducer.COOP_MESSAGES_EXCHANGE,
                ExchangeType.SHARDING
            ).bind()

        val tradeMessagesProducer: InteractionProducer<TradeInternalMessages.UserInputMessage> =
            InteractionProducer.create(
                chatConfig.rabbitConfig,
                TradeInternalMessages.UserInputMessage.serializer(),
                InteractionProducer.TRADE_MESSAGES_EXCHANGE,
                ExchangeType.SHARDING
            ).bind()

        val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage> =
            InteractionProducer.create(
                chatConfig.rabbitConfig,
                EquipmentInternalMessage.serializer(),
                InteractionProducer.EQ_CHANGE_EXCHANGE,
                ExchangeType.SHARDING
            ).bind()

        val logsProducer: InteractionProducer<LogsMessage> =
            InteractionProducer.create(
                chatConfig.rabbitConfig,
                LogsMessage.serializer(),
                InteractionProducer.LOGS_EXCHANGE,
                ExchangeType.FANOUT
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
                systemOutputProducer,
                coopMessagesProducer,
                tradeMessagesProducer,
                equipmentChangeProducer,
                logsProducer
            )
        )

        awaitCancellation()
    }
}

fun chatModule(
    chatConfig: ChatConfig,
    sessionStorage: SessionStorage<WebSocketSession>,
    messagePasser: MessagePasser<Message>,
    interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    coopMessagesProducer: InteractionProducer<CoopInternalMessages>,
    tradeMessagesProducer: InteractionProducer<TradeInternalMessages.UserInputMessage>,
    equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>,
    logsProducer: InteractionProducer<LogsMessage>
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
                interactionProducer,
                coopMessagesProducer,
                tradeMessagesProducer,
                equipmentChangeProducer,
                logsProducer
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
    configureEquipmentRoute()
}
