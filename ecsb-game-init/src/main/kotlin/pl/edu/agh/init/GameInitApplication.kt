package pl.edu.agh.init

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
import pl.edu.agh.assets.SavedAssetsModule.getKoinSavedAssetsModule
import pl.edu.agh.assets.route.AssetRoute.configureGameAssetsRoutes
import pl.edu.agh.auth.AuthModule.getKoinAuthModule
import pl.edu.agh.auth.route.AuthRoutes.configureAuthRoutes
import pl.edu.agh.auth.service.configureGameUserSecurity
import pl.edu.agh.auth.service.configureLoginUserSecurity
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.init.GameInitModule.getKoinGameInitModule
import pl.edu.agh.init.route.InitRoutes.configureGameInitRoutes
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.landingPage.redis.LandingPageRedisCreationParams
import pl.edu.agh.landingPage.route.LandingPageRoutes.configureLandingPageRoutes
import pl.edu.agh.landingPage.service.LandingPageMessagePasser
import pl.edu.agh.logs.domain.LogsMessage
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.moving.redis.MovementRedisCreationParams
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.ConfigUtils.getConfigOrThrow
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

fun main(): Unit = SuspendApp {
    val gameInitConfig = getConfigOrThrow<GameInitConfig>()
    val landingPageSessionStorage = SessionStorageImpl()

    resourceScope {
        val redisMovementDataConnector = RedisJsonConnector.createAsResource(
            MovementRedisCreationParams(gameInitConfig.redisConfig)
        ).bind()

        val landingPageRedisConnector = RedisJsonConnector.createAsResource(
            LandingPageRedisCreationParams(gameInitConfig.redisConfig)
        ).bind()

        val connection = RabbitFactory.getConnection(gameInitConfig.rabbitConfig).bind()

        RabbitFactory.getChannelResource(connection).use {
            RabbitMainExchangeSetup.setup(it)
        }

        DatabaseConnector.initDBAsResource().bind()

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

        val logsProducer: InteractionProducer<LogsMessage> =
            InteractionProducer.create(
                LogsMessage.serializer(),
                InteractionProducer.LOGS_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        server(
            Netty,
            host = gameInitConfig.httpConfig.host,
            port = gameInitConfig.httpConfig.port,
            preWait = gameInitConfig.httpConfig.preWait,
            module = gameInitModule(
                gameInitConfig,
                redisMovementDataConnector,
                logsProducer,
                landingPageProducer,
                landingPageSessionStorage,
                landingPageRedisConnector
            )
        )

        awaitCancellation()
    }
}

fun gameInitModule(
    gameInitConfig: GameInitConfig,
    redisMovementDataConnector: RedisJsonConnector<PlayerId, PlayerPosition>,
    logsProducer: InteractionProducer<LogsMessage>,
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
            getKoinAuthModule(gameInitConfig.jwt),
            getKoinGameInitModule(
                gameInitConfig.gameToken,
                redisMovementDataConnector,
                landingPageProducer
            ),
            getKoinSavedAssetsModule(gameInitConfig.savedAssetsConfig)
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
        configureGameUserSecurity(gameInitConfig.gameToken)
        configureLoginUserSecurity(gameInitConfig.jwt)
        configurePrometheusRoute()
    }
    routing {
        authenticate("metrics") {
            get("/metrics") {
                call.respond(appMicrometerRegistry.scrape())
            }
        }
    }
    configureAuthRoutes()
    configureGameInitRoutes()
    configureGameAssetsRoutes()
    configureLandingPageRoutes(
        gameInitConfig.gameToken,
        logsProducer,
        landingPageSessionStorage,
        landingPageProducer,
        landingPageRedisConnector,
        landingPageGauge
    )
}
