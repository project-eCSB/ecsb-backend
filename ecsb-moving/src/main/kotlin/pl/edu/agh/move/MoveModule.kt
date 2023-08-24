package pl.edu.agh.move

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.dsl.module
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.MoveMessage
import pl.edu.agh.redis.RedisHashMapConnector

object MoveModule {
    fun Application.getKoinMoveModule(
        sessionStorage: SessionStorage<WebSocketSession>,
        redisMovementDataConnector: RedisHashMapConnector<PlayerId, PlayerPosition>,
        moveMessageInteractionProducer: InteractionProducer<MoveMessage>
    ) = module {
        single<SessionStorage<WebSocketSession>> { sessionStorage }
        single<InteractionProducer<MoveMessage>> { moveMessageInteractionProducer }
        single { MovementDataConnector(redisMovementDataConnector) }
    }
}
