package pl.edu.agh.move

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
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.move.MovingModule.getKoinMovingModule
import pl.edu.agh.move.domain.MoveMessageADT
import pl.edu.agh.move.route.MoveRoutes.configureMoveRoutes
import pl.edu.agh.move.service.MovementMessagePasser
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.moving.redis.MovementRedisCreationParams
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType
import java.time.Duration

fun main(): Unit = SuspendApp {
    val movingConfig = ConfigUtils.getConfigOrThrow<MovingConfig>()
    val sessionStorage = SessionStorageImpl()

    resourceScope {
        val redisMovementDataConnector = RedisJsonConnector.createAsResource(
            MovementRedisCreationParams(movingConfig.redisConfig)
        ).bind()

        DatabaseConnector.initDBAsResource().bind()

        val connection = RabbitFactory.getConnection(movingConfig.rabbitConfig).bind()

        RabbitFactory.getChannelResource(connection).use {
            RabbitMainExchangeSetup.setup(it)
        }

        val interactionRabbitMessagePasser = MovementMessagePasser(sessionStorage)

        InteractionConsumerFactory.create(
            interactionRabbitMessagePasser,
            System.getProperty("rabbitHostTag", "develop"),
            connection
        ).bind()

        val movementMessageProducer: InteractionProducer<MoveMessageADT> =
            InteractionProducer.create(
                MoveMessageADT.serializer(),
                InteractionProducer.MOVEMENT_MESSAGES_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        server(
            Netty,
            host = movingConfig.httpConfig.host,
            port = movingConfig.httpConfig.port,
            preWait = movingConfig.httpConfig.preWait,
            module = moveModule(movingConfig, sessionStorage, redisMovementDataConnector, movementMessageProducer)
        )

        awaitCancellation()
    }
}

fun moveModule(
    movingConfig: MovingConfig,
    sessionStorage: SessionStorage<WebSocketSession>,
    redisMovementDataConnector: RedisJsonConnector<PlayerId, PlayerPosition>,
    moveMessageInteractionProducer: InteractionProducer<MoveMessageADT>
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
            getKoinMovingModule(
                sessionStorage,
                redisMovementDataConnector,
                moveMessageInteractionProducer
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
    configurePrometheusRoute()
    routing {
        authenticate("metrics") {
            get("/metrics") {
                call.respond(appMicrometerRegistry.scrape())
            }
        }
    }
    configureMoveRoutes(movingConfig.gameToken)
}
