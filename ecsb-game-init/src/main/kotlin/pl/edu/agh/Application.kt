package pl.edu.agh

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import pl.edu.agh.assets.SavedAssetsModule.getKoinSavedAssetsModule
import pl.edu.agh.assets.route.AssetRoute.configureGameAssetsRoutes
import pl.edu.agh.auth.AuthModule.getKoinAuthModule
import pl.edu.agh.auth.route.AuthRoutes.configureAuthRoutes
import pl.edu.agh.auth.service.configureSecurity
import pl.edu.agh.game.GameModule.getKoinGameModule
import pl.edu.agh.gameInit.route.InitRoutes.configureGameInitRoutes
import pl.edu.agh.utils.ConfigUtils.getConfigOrThrow
import pl.edu.agh.utils.DatabaseConnector

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
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
    val gameInitConfig = getConfigOrThrow<GameInitConfig>()
    install(Koin) {
        modules(
            getKoinAuthModule(gameInitConfig.jwt),
            getKoinGameModule(gameInitConfig.redis, gameInitConfig.gameToken, gameInitConfig.defaultAssets),
            getKoinSavedAssetsModule(gameInitConfig.savedAssets)
        )
    }
    DatabaseConnector.initDB()
    configureSecurity(gameInitConfig.jwt, gameInitConfig.gameToken)
    configureAuthRoutes()
    configureGameInitRoutes()
    configureGameAssetsRoutes()
}
