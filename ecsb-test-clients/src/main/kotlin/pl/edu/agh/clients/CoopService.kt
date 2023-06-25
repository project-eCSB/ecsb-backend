package pl.edu.agh.clients

import arrow.fx.coroutines.parMap
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import pl.edu.agh.auth.service.JWTTokenSimple
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.NonEmptyMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class CoopService(
    val client: HttpClient,
    val chatUrl: String,
    val credentials: NonEmptyMap<PlayerId, JWTTokenSimple>
) {
    val atomicInteger = AtomicInteger(0)
    val connections = ConcurrentHashMap<PlayerId, WebSocketSession>()

    suspend fun runCommands(commands: List<Pair<PlayerId, ChatMessageADT.UserInputMessage>>) {
        suspend fun runCommandsInternal() {
            commands.forEach { (playerId, message) ->
                connections.get(playerId)?.send(
                    Frame.Text(
                        Json.encodeToString(ChatMessageADT.UserInputMessage.serializer(), message)
                    )
                )

                delay(3.seconds)
            }

            throw IllegalStateException("elo")
        }

        credentials.map.toList().parMap {
            client.webSocket("$chatUrl/ws?gameToken=${it.second}") {

                connections[it.first] = this
                val value = atomicInteger.addAndGet(1)
                if (value == credentials.size) {
                    runCommandsInternal()
                }
                awaitCancellation()
            }
        }

    }

}