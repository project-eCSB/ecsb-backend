@file:OptIn(ExperimentalTime::class)

package pl.edu.agh.clients

import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import arrow.fx.coroutines.parMap
import arrow.fx.coroutines.parZip
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pl.edu.agh.auth.domain.LoginCredentials
import pl.edu.agh.auth.domain.Password
import pl.edu.agh.auth.service.JWTTokenSimple
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.Direction
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.travel.domain.TravelName
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

internal fun <T> Flow<T>.repeatN(repeatNum: Long): Flow<T> =
    flow {
        for (i in 1..repeatNum) {
            collect {
                emit(it)
            }
        }
    }

@OptIn(ExperimentalTime::class)
suspend fun runChatConsumer(client: HttpClient, ecsbChatUrl: String, gameToken: String) {
    client.webSocket("$ecsbChatUrl/ws?gameToken=$gameToken") {
        val messages = listOf<ChatMessageADT.UserInputMessage>(
            ChatMessageADT.UserInputMessage.WorkshopChoosing.WorkshopChoosingStart,
            ChatMessageADT.UserInputMessage.WorkshopChoosing.WorkshopChoosingStop
        ).map {
            Frame.Text(
                Json.encodeToString(ChatMessageADT.UserInputMessage.serializer(), it)
            )
        }

        val a = flow { emit(1) }.repeatN(250_000).metered(0.5.seconds).mapIndexed { i, _ ->
            messages.map {
                println("sending $it")
                this.outgoing.send(it)
            }
        }

        val b = this.incoming.consumeAsFlow().map {
            when (it) {
                is Frame.Text -> println(it.readText())
                else -> println("unknown frame")
            }
        }
        parZip({ a.collect() }, { b.collect() }, { x, y -> x })
    }
}

@OptIn(ExperimentalTime::class)
suspend fun runMoving(client: HttpClient, ecsbMoveUrl: String, gameToken: String) {
    client.webSocket("$ecsbMoveUrl/ws?gameToken=$gameToken") {
        this.outgoing.send(
            Frame.Text(
                Json.encodeToString(
                    MessageADT.UserInputMessage.serializer(),
                    MessageADT.UserInputMessage.SyncRequest()
                )
            )
        )
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

suspend fun doTravel(client: HttpClient, ecsbChatUrlHttp: String, gameToken: JWTTokenSimple) {
    val status = client.post("$ecsbChatUrlHttp/travel") {
        bearerAuth(gameToken)
        contentType(ContentType.Application.Json)
        setBody(TravelName("paris"))
    }.status
    println(status)
}

@OptIn(FlowPreview::class, ExperimentalTime::class)
fun main(args: Array<String>) = runBlocking {
    val (min, max) = args.toList().map { it.toInt() }.take(2)
    BenchmarkSimpleChatMessages().runBenchmark(min, max)
    TODO()

    val gameInitUrl = "http://ecsb-big.duckdns.org:2136"
    val ecsbChatUrl = "ws://ecsb-1.duckdns.org:2138"
    val ecsbChatUrlHttp = "http://ecsb-1.duckdns.org:2138"
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

    val credentialsLogins = (min..max).map { "eloelo1$it@elo.pl" }

    credentialsLogins.mapIndexed { x, y -> x to y }.parMap { (index, it) ->
        val gameInitService = GameInitService(client, gameInitUrl)

        val loginCredentials = LoginCredentials(it, Password("123123123"))
        println("Before call")
        val gameToken = gameInitService.getGameToken(loginCredentials, "4e732c")
        println("After login call")
        if (index % 2 == 0) {
            runChatConsumer(client, ecsbChatUrl, gameToken)
        } else {
            flow { emit(1) }.repeatN(250_000).metered(0.5.seconds).parMap {
                doProduction(client, ecsbChatUrlHttp, gameToken)
//                doTravel(client, ecsbChatUrlHttp, gameToken)
            }.collect()
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
