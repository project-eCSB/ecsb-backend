package pl.edu.agh.clients

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import arrow.fx.coroutines.parMap
import arrow.fx.coroutines.parZip
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import pl.edu.agh.auth.domain.input.LoginRequest
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.interaction.domain.Message
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.Sensitive
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class BenchmarkSimpleChatMessages {

    val gameInitUrl = "https://ecsb-dev.mooo.com/api/init"
    val ecsbChatUrl = "wss://ecsb-dev.mooo.com/chat"
    val ecsbChatUrlHttp = "https://ecsb-dev.mooo.com/chat"
    val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
//        install(Logging) {
//            logger = Logger.DEFAULT
//            level = LogLevel.HEADERS
//        }
        install(WebSockets)
    }

    private val times = mutableMapOf<SentTime, Option<ReceiveTime>>()

    fun calcTime() {
        val allTimeDiffs = times.toMap().toList().mapNotNull { (sent, receive) ->
            receive.getOrNull()?.let { it - sent }
        }

        val averageMillis = allTimeDiffs.average()
        println("Average millis after ${times.size}/${allTimeDiffs.size}: ${averageMillis}ms")
    }

    @OptIn(ExperimentalTime::class)
    suspend fun runChatConsumer(client: HttpClient, ecsbChatUrl: String, gameToken: String) {
        client.webSocket("$ecsbChatUrl/ws?gameToken=$gameToken") {
            val a = flow { emit(1) }.repeatN(25_000).metered(0.5.seconds).mapIndexed { i, _ ->
                val sentTime = LocalDateTime.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                times[sentTime] = none()
                val message =
                    CoopMessages.CoopUserInputMessage.StartPlanning(TravelName(sentTime.toString())).let {
                        Frame.Text(
                            Json.encodeToString(ChatMessageADT.UserInputMessage.serializer(), it)
                        )
                    }
                this.outgoing.send(message)
            }

            val b = this.incoming.consumeAsFlow().map {
                when (it) {
                    is Frame.Text -> {
                        val text = it.readText()
                        println(text)
                        val json = Json.decodeFromString(Message.serializer(ChatMessageADT.serializer()), text)
                        when (json.message) {
                            is CoopMessages.CoopSystemOutputMessage.StartPlanningSystem -> {
                                println("elo")
                                val sentAtStr =
                                    (json.message as CoopMessages.CoopSystemOutputMessage.StartPlanningSystem).travelName.value.toLong()
                                val now = LocalDateTime.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                                times[sentAtStr] = now.some()
                            }

                            else -> {
                                println("nie elo")
                            }
                        }
                    }

                    else -> println("unknown frame")
                }
            }
            parZip({ a.collect() }, { b.collect() }, { x, y -> x })
        }
    }

    @OptIn(ExperimentalTime::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
    suspend fun runBenchmark(min: Int, max: Int) {
        val credentialsLogins = (min..max).map { "elo1$it@elo.pl" }

        val flow = flowOf(1).repeatN(250_000).metered(5.seconds).map {
            calcTime()
        }

        val a: suspend () -> Unit = {
            credentialsLogins.mapIndexed { x, y -> x to y }.parMap { (index, it) ->
                val gameInitService = GameInitService(client, gameInitUrl)

                val loginRequest = LoginRequest(it, Sensitive("123123123"))
                println("Before call")
                val gameToken = gameInitService.getGameToken(loginRequest, "4e732c")
                println("After login call")
                runChatConsumer(client, ecsbChatUrl, gameToken)
            }
        }

        parZip({ flow.collect() }, { a() }, { x, y -> {} })
    }
}

typealias SentTime = Long
typealias ReceiveTime = Long
