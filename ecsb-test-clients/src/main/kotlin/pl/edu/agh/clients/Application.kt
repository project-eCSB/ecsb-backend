package pl.edu.agh.clients

import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pl.edu.agh.auth.domain.LoginCredentials
import pl.edu.agh.auth.domain.Password
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.Direction
import pl.edu.agh.move.domain.MessageADT
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private fun <T> Flow<T>.repeatN(repeatNum: Long): Flow<T> =
    flow {
        for (i in 1..repeatNum) {
            collect {
                emit(it)
            }
        }
    }

@OptIn(ExperimentalTime::class)
fun main(args: Array<String>) = runBlocking {
    val gameInitUrl = "http://ecsb-big.duckdns.org:2136"
    val ecsbMoveUrl = "ws://localhost:8085" // "ws://ecsb-big.duckdns.org/move"
    val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
        install(WebSockets)
    }

    val gameInitService = GameInitService(client, gameInitUrl)

    val loginCredentials = LoginCredentials("eloelo14@elo.pl", Password("123123123"))
    println("Before call")
    val gameToken = gameInitService.getGameToken(loginCredentials, "3c59dc")
    println("After login call")

    client.webSocket("$ecsbMoveUrl/ws?gameToken=$gameToken") {
        flow { emit(1) }.repeatN(33).metered(2.seconds).mapIndexed { i, _ ->
            val coords = Coordinates(i, 10)
            println("sending coordinates $coords")
            this.outgoing.send(
                Frame.Text(
                    Json.encodeToString(
                        MessageADT.UserInputMessage.serializer(),
                        MessageADT.UserInputMessage.Move(
                            coords = coords,
                            direction = Direction.DOWN
                        )
                    )
                )
            )
        }.collect()
    }
}
