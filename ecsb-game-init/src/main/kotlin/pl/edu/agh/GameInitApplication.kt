package pl.edu.agh

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
import pl.edu.agh.assets.SavedAssetsModule.getKoinSavedAssetsModule
import pl.edu.agh.assets.route.AssetRoute.configureGameAssetsRoutes
import pl.edu.agh.auth.AuthModule.getKoinAuthModule
import pl.edu.agh.auth.route.AuthRoutes.configureAuthRoutes
import pl.edu.agh.auth.service.configureSecurity
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.GameModule.getKoinGameModule
import pl.edu.agh.init.route.InitRoutes.configureGameInitRoutes
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.ConfigUtils.getConfigOrThrow
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType

fun main(): Unit = SuspendApp {
    val gameInitConfig = getConfigOrThrow<GameInitConfig>()

    resourceScope {
        val redisMovementDataConnector = RedisJsonConnector.createAsResource(
            RedisJsonConnector.Companion.MovementCreationParams(gameInitConfig.redis)
        ).bind()


        val connection = RabbitFactory.getConnection(gameInitConfig.rabbitConfig).bind()

        DatabaseConnector.initDBAsResource().bind()

        val logsProducer: InteractionProducer<LandingPageMessage> =
            InteractionProducer.create(
                LandingPageMessage.serializer(),
                InteractionProducer.LANDING_PAGE_MESSAGES_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        server(
            Netty,
            host = gameInitConfig.httpConfig.host,
            port = gameInitConfig.httpConfig.port,
            preWait = gameInitConfig.httpConfig.preWait,
            module = gameInitModule(gameInitConfig, redisMovementDataConnector, logsProducer)
        )

        awaitCancellation()
    }
}

fun gameInitModule(
    gameInitConfig: GameInitConfig,
    redisMovementDataConnector: RedisJsonConnector<PlayerId, PlayerPosition>,
    logsProducer: InteractionProducer<LandingPageMessage>
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
            getKoinGameModule(
                gameInitConfig.gameToken,
                redisMovementDataConnector,
                gameInitConfig.defaultAssets,
                logsProducer
            ),
            getKoinSavedAssetsModule(gameInitConfig.savedAssets)
        )
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
    configureSecurity(gameInitConfig.jwt, gameInitConfig.gameToken)
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
}
