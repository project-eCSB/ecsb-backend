package pl.edu.agh

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
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
import pl.edu.agh.gameInit.route.InitRoutes.configureGameInitRoutes
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.ConfigUtils.getConfigOrThrow
import pl.edu.agh.utils.DatabaseConnector

fun main(): Unit = SuspendApp {
    val gameInitConfig = getConfigOrThrow<GameInitConfig>()

    resourceScope {
        val redisMovementDataConnector = RedisHashMapConnector.createAsResource(
            RedisHashMapConnector.Companion.MovementCreationParams(gameInitConfig.redis)
        ).bind()

        DatabaseConnector.initDBAsResource().bind()

        server(
            Netty,
            host = gameInitConfig.httpConfig.host,
            port = gameInitConfig.httpConfig.port,
            preWait = gameInitConfig.httpConfig.preWait,
            module = gameInitModule(gameInitConfig, redisMovementDataConnector)
        )

        awaitCancellation()
    }
}

fun gameInitModule(
    gameInitConfig: GameInitConfig,
    redisMovementDataConnector: RedisHashMapConnector<PlayerId, PlayerPosition>
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
            getKoinAuthModule(gameInitConfig.jwt, gameInitConfig.gameToken),
            getKoinGameModule(gameInitConfig.gameToken, redisMovementDataConnector, gameInitConfig.defaultAssets),
            getKoinSavedAssetsModule(gameInitConfig.savedAssets)
        )
    }
    configureSecurity(gameInitConfig.jwt, gameInitConfig.gameToken)
    configureAuthRoutes()
    configureGameInitRoutes()
    configureGameAssetsRoutes()
}
