@file:OptIn(ExperimentalTime::class)

package pl.edu.agh.clients

import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import arrow.fx.coroutines.parMap
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pl.edu.agh.auth.domain.LoginCredentials
import pl.edu.agh.auth.domain.Password
import pl.edu.agh.auth.service.JWTTokenSimple
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

suspend fun runChatConsumer(client: HttpClient, ecsbChatUrl: String, gameToken: String) {
    client.webSocket("$ecsbChatUrl/ws?gameToken=$gameToken") {
        this.incoming.consumeAsFlow().map {
            when (it) {
                is Frame.Text -> println(it.readText())
                else -> println("unknown frame")
            }
        }.collect()
    }
}

@OptIn(ExperimentalTime::class)
suspend fun runMoving(client: HttpClient, ecsbMoveUrl: String, gameToken: String) {
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

suspend fun doProduction(client: HttpClient, ecsbChatUrlHttp: String, gameToken: JWTTokenSimple) {
    val status = client.post("$ecsbChatUrlHttp/production") {
        bearerAuth(gameToken)
        contentType(ContentType.Application.Json)
        setBody(1)
    }.status
    println(status)
}

fun main(args: Array<String>) = runBlocking {
    val gameInitUrl = "http://ecsb-big.duckdns.org:2136"
    val chatUrl = "http://ecsb-big.duckdns.org:2138"
    val ecsbMoveUrl = "ws://localhost:8085" // "ws://ecsb-big.duckdns.org/move"
    val ecsbChatUrl = "ws://localhost:2138"
    val ecsbChatUrlHttp = "http://localhost:2138"
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

    val credentialsLogins = (36..38).map { "eloelo1$it@elo.pl" }

    val interactionService = InteractionService(client, gameInitUrl, chatUrl)

    credentialsLogins.mapIndexed { x, y -> x to y }.parMap { (index, it) ->
        val gameInitService = GameInitService(client, gameInitUrl)

        val loginCredentials = LoginCredentials(it, Password("123123123"))
        println("Before call")
        val gameToken = gameInitService.getGameToken(loginCredentials, "3c59dc")
        interactionService.produce(gameToken, 2)
        println("After login call")
        if (index % 2 == 0) {
            runChatConsumer(client, ecsbChatUrl, gameToken)
        } else {
            doProduction(client, ecsbChatUrlHttp, gameToken)
        }
    }

//    client.webSocket("$ecsbMoveUrl/ws?gameToken=$gameToken") {
//        flow { emit(1) }.repeatN(33).metered(2.seconds).mapIndexed { i, _ ->
//            val coords = Coordinates(i, 10)
//            println("sending coordinates $coords")
//            this.outgoing.send(
//                Frame.Text(
//                    Json.encodeToString(
//                        MessageADT.UserInputMessage.serializer(),
//                        MessageADT.UserInputMessage.Move(
//                            coords = coords,
//                            direction = Direction.DOWN
//                        )
//                    )
//                )
//            )
//        }.collect()
//    }
    Unit
}
