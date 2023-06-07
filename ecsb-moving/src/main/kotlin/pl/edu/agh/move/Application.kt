package pl.edu.agh.move

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.websocket.*
import org.koin.ktor.plugin.Koin
import pl.edu.agh.auth.AuthModule.getKoinAuthModule
import pl.edu.agh.auth.service.configureSecurity
import pl.edu.agh.game.GameModule.getKoinGameModule
import pl.edu.agh.move.MoveModule.getKoinMoveModule
import pl.edu.agh.move.route.MoveRoutes.configureMoveRoutes
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import java.time.Duration

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
    val movingConfig = ConfigUtils.getConfigOrThrow<MovingConfig>()
    install(Koin) {
        modules(
            getKoinAuthModule(movingConfig.jwt, movingConfig.gameToken),
            getKoinMoveModule(movingConfig.redis),
            getKoinGameModule(movingConfig.redis, movingConfig.gameToken, movingConfig.defaultAssets)
        )
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    DatabaseConnector.initDB()
    configureSecurity(movingConfig.jwt, movingConfig.gameToken)
    configureMoveRoutes(movingConfig.gameToken)
}
