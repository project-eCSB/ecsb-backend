package pl.edu.agh.chat

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.awaitCancellation
import org.koin.ktor.plugin.Koin
import pl.edu.agh.PrometheusRoute.configurePrometheusRoute
import pl.edu.agh.auth.service.configureGameUserSecurity
import pl.edu.agh.chat.ChatModule.getKoinChatModule
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.route.ChatRoutes.configureChatRoutes
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.route.EquipmentRoute.configureEquipmentRoute
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionMessagePasser
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.LandingPageMessagePasser
import pl.edu.agh.landingPage.LandingPageRedisCreationParams
import pl.edu.agh.landingPage.LandingPageRoutes.configureLandingPageRoutes
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.logs.domain.LogsMessage
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.moving.redis.MovementRedisCreationParams
import pl.edu.agh.production.domain.WorkshopInternalMessages
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

fun main(): Unit = SuspendApp {
    val chatConfig = ConfigUtils.getConfigOrThrow<ChatConfig>()
    val sessionStorage = SessionStorageImpl()
    val landingPageSessionStorage = SessionStorageImpl()

    resourceScope {
        val redisMovementDataConnector = RedisJsonConnector.createAsResource(
            MovementRedisCreationParams(chatConfig.redisConfig)
        ).bind()

        val landingPageRedisConnector = RedisJsonConnector.createAsResource(
            LandingPageRedisCreationParams(chatConfig.redisConfig)
        ).bind()

        DatabaseConnector.initDBAsResource().bind()

        val connection = RabbitFactory.getConnection(chatConfig.rabbitConfig).bind()

        RabbitFactory.getChannelResource(connection).use {
            RabbitMainExchangeSetup.setup(it)
        }

        InteractionConsumerFactory.create(
            InteractionMessagePasser(sessionStorage, redisMovementDataConnector),
            System.getProperty("rabbitHostTag", "develop"),
            connection
        ).bind()

        InteractionConsumerFactory.create(
            LandingPageMessagePasser(landingPageSessionStorage),
            System.getProperty("rabbitHostTag", "develop"),
            connection
        ).bind()

        val landingPageProducer: InteractionProducer<LandingPageMessage> =
            InteractionProducer.create(
                LandingPageMessage.serializer(),
                InteractionProducer.LANDING_PAGE_MESSAGES_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        val systemOutputProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage> =
            InteractionProducer.create(
                ChatMessageADT.SystemOutputMessage.serializer(),
                InteractionProducer.INTERACTION_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        val coopMessagesProducer: InteractionProducer<CoopInternalMessages.UserInputMessage> =
            InteractionProducer.create(
                CoopInternalMessages.UserInputMessage.serializer(),
                InteractionProducer.COOP_MESSAGES_EXCHANGE,
                ExchangeType.SHARDING,
                connection
            ).bind()

        val tradeMessagesProducer: InteractionProducer<TradeInternalMessages.UserInputMessage> =
            InteractionProducer.create(
                TradeInternalMessages.UserInputMessage.serializer(),
                InteractionProducer.TRADE_MESSAGES_EXCHANGE,
                ExchangeType.SHARDING,
                connection
            ).bind()

        val logsProducer: InteractionProducer<LogsMessage> =
            InteractionProducer.create(
                LogsMessage.serializer(),
                InteractionProducer.LOGS_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        val timeProducer: InteractionProducer<TimeInternalMessages> =
            InteractionProducer.create(
                TimeInternalMessages.serializer(),
                InteractionProducer.TIME_MESSAGES_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        val workshopMessagesProducer: InteractionProducer<WorkshopInternalMessages> =
            InteractionProducer.create(
                WorkshopInternalMessages.serializer(),
                InteractionProducer.WORKSHOP_EXCHANGE,
                ExchangeType.SHARDING,
                connection
            ).bind()

        server(
            Netty,
            host = chatConfig.httpConfig.host,
            port = chatConfig.httpConfig.port,
            preWait = chatConfig.httpConfig.preWait,
            module = chatModule(
                chatConfig,
                sessionStorage,
                systemOutputProducer,
                coopMessagesProducer,
                tradeMessagesProducer,
                logsProducer,
                timeProducer,
                workshopMessagesProducer,
                landingPageProducer,
                landingPageSessionStorage,
                landingPageRedisConnector
            )
        )

        awaitCancellation()
    }
}

fun chatModule(
    chatConfig: ChatConfig,
    sessionStorage: SessionStorage<WebSocketSession>,
    interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    coopMessagesProducer: InteractionProducer<CoopInternalMessages.UserInputMessage>,
    tradeMessagesProducer: InteractionProducer<TradeInternalMessages.UserInputMessage>,
    logsProducer: InteractionProducer<LogsMessage>,
    timeProducer: InteractionProducer<TimeInternalMessages>,
    workshopMessagesProducer: InteractionProducer<WorkshopInternalMessages>,
    landingPageProducer: InteractionProducer<LandingPageMessage>,
    landingPageSessionStorage: SessionStorage<WebSocketSession>,
    landingPageRedisConnector: RedisJsonConnector<PlayerId, PlayerId>
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
            getKoinChatModule(
                sessionStorage,
                interactionProducer,
                coopMessagesProducer,
                tradeMessagesProducer,
                workshopMessagesProducer,
                logsProducer,
                landingPageProducer
            )
        )
    }
    install(WebSockets) {
        pingPeriodMillis = 0
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val landingPageGauge = AtomicLong(0)
    appMicrometerRegistry.gauge("landing.playerCount", Tags.empty(), landingPageGauge) {
        landingPageGauge.get().toDouble()
    }

    val playerCountGauge = AtomicLong(0)
    appMicrometerRegistry.gauge("chat.playerCount", Tags.empty(), playerCountGauge) {
        playerCountGauge.get().toDouble()
    }

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            ClassLoaderMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics(),
            UptimeMetrics()
        )
    }
    authentication {
        configureGameUserSecurity(chatConfig.gameToken)
        configurePrometheusRoute()
    }
    routing {
        authenticate("metrics") {
            get("/metrics") {
                call.respond(appMicrometerRegistry.scrape())
            }
        }
    }
    configureChatRoutes(chatConfig.gameToken, logsProducer, timeProducer, playerCountGauge, appMicrometerRegistry)
    configureLandingPageRoutes(
        chatConfig.gameToken,
        landingPageSessionStorage,
        landingPageProducer,
        logsProducer,
        landingPageRedisConnector,
        landingPageGauge
    )
    configureEquipmentRoute()
}
