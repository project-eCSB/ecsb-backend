@file:OptIn(DelicateCoroutinesApi::class)

package pl.edu.agh.clients

import arrow.fx.coroutines.parMap
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import pl.edu.agh.auth.service.JWTTokenSimple
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.NonEmptyMap
import java.util.concurrent.ConcurrentHashMap

class ChatWSService(
    private val client: HttpClient,
    private val chatUrl: String,
    private val credentials: NonEmptyMap<PlayerId, JWTTokenSimple>
) {
    private val connections = ConcurrentHashMap<PlayerId, WebSocketSession>()

    suspend fun start() = GlobalScope.launch {
        credentials.map.toList().parMap {
            client.webSocket("$chatUrl/ws?gameToken=${it.second}") {
                connections[it.first] = this
                awaitCancellation()
            }
        }
    }

    suspend fun sendCommand(playerId: PlayerId, message: ChatMessageADT.UserInputMessage) {
        connections[playerId]?.send(
            Frame.Text(
                Json.encodeToString(ChatMessageADT.UserInputMessage.serializer(), message)
            )
        )
    }
}
