package pl.edu.agh.move

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
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.GameModule.getKoinGameModule
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.messages.service.simple.SimpleMessagePasser
import pl.edu.agh.move.MoveModule.getKoinMoveModule
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.route.MoveRoutes.configureMoveRoutes
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import java.time.Duration

fun main(): Unit = SuspendApp {
    val movingConfig = ConfigUtils.getConfigOrThrow<MovingConfig>()
    val sessionStorage = SessionStorageImpl()

    resourceScope {
        val redisMovementDataConnector = RedisHashMapConnector.createAsResource(
            movingConfig.redis,
            RedisHashMapConnector.MOVEMENT_DATA_PREFIX,
            GameSessionId::toName,
            PlayerId.serializer(),
            PlayerPosition.serializer()
        ).bind()

        DatabaseConnector.initDBAsResource().bind()

        val simpleMessagePasser = SimpleMessagePasser.create(sessionStorage, Message.serializer()).bind()

        server(
            Netty,
            host = movingConfig.httpConfig.host,
            port = movingConfig.httpConfig.port,
            preWait = movingConfig.httpConfig.preWait,
            module = moveModule(movingConfig, sessionStorage, simpleMessagePasser, redisMovementDataConnector)
        )

        awaitCancellation()
    }
}

fun moveModule(
    movingConfig: MovingConfig,
    sessionStorage: SessionStorage<WebSocketSession>,
    messagePasser: MessagePasser<Message>,
    redisMovementDataConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>
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
            getKoinAuthModule(movingConfig.jwt, movingConfig.gameToken),
            getKoinMoveModule(sessionStorage, redisMovementDataConnector, messagePasser),
            getKoinGameModule(movingConfig.gameToken, redisMovementDataConnector, movingConfig.defaultAssets)
        )
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    configureSecurity(movingConfig.jwt, movingConfig.gameToken)
    configureMoveRoutes(movingConfig.gameToken)
}
