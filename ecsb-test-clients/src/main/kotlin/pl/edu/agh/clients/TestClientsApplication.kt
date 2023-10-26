@file:OptIn(ExperimentalTime::class)

package pl.edu.agh.clients

import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pl.edu.agh.auth.domain.LoginCredentials
import pl.edu.agh.auth.domain.Password
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.Direction
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonNegFloat.Companion.nonNeg
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt.Companion.pos
import pl.edu.agh.utils.nonEmptyMapOf
import kotlin.random.Random
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
suspend fun runMoving(client: HttpClient, ecsbMoveUrl: String, gameToken: String, num: Long) {
    client.webSocket("$ecsbMoveUrl/ws?gameToken=$gameToken") {
        this.outgoing.send(
            Frame.Text(
                Json.encodeToString(
                    MessageADT.UserInputMessage.serializer(),
                    MessageADT.UserInputMessage.SyncRequest()
                )
            )
        )
        flow { emit(1) }.repeatN(num).metered(2.seconds).mapIndexed { i, _ ->
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

@OptIn(DelicateCoroutinesApi::class)
fun main(args: Array<String>) = runBlocking {
    val (min, max) = args.toList().map { it.toInt() }.take(2)

    val gameInitUrl = "https://ecsb.chcesponsora.pl/api/init"
    val ecsbChatUrlWs = "wss://ecsb.chcesponsora.pl/chat"
    val ecsbMoveUrlWs = "wss://ecsb.chcesponsora.pl/move"
    val ecsbChatUrlHttp = "https://ecsb.chcesponsora.pl/chat"
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

    val loginCredentialsFun = { x: String -> LoginCredentials(x, Password("123123123")) }

    val gameInitService = GameInitService(client, gameInitUrl)
    val tokens = (min..max).map {
        gameInitService.getGameToken(loginCredentialsFun("eloelo1$it@elo.pl"), "3295c7")
    }

    tokens.forEach {
        GlobalScope.launch {
            runMoving(client, ecsbMoveUrlWs, it, Random.nextLong(123, 100000))
        }
    }


    val travelName = TravelName("Berlin")
    val resourcesDecide = ResourcesDecideValues(
        PlayerId("eloelo1$min@elo.pl"),
        05f.nonNeg,
        nonEmptyMapOf(
            GameResourceName("leather") to NonNegInt(1),
            GameResourceName("weave") to NonNegInt(1),
            GameResourceName("orch") to NonNegInt(1)
        )
    )

    val gameService = GameService(client, ecsbChatUrlHttp, ecsbChatUrlWs, gameInitUrl, "3295c7")

    val (firstId, secondId) = gameService.start(loginCredentialsFun, listOf(min, max)).take(2)
    val commands = listOf<Triple<CommandEnum, PlayerId, Any>>(
        Triple(CommandEnum.PRODUCTION, firstId, 1.pos),
        Triple(CommandEnum.TRAVEL, firstId, travelName),
        Triple(CommandEnum.CHAT_WS, firstId, ChatMessageADT.UserInputMessage.WorkshopChoosing.WorkshopChoosingStart),
        Triple(CommandEnum.CHAT_WS, firstId, CoopMessages.CoopUserInputMessage.StartPlanning(travelName)),
        Triple(CommandEnum.CHAT_WS, firstId, CoopMessages.CoopUserInputMessage.ProposeCompany(travelName, secondId)),
        Triple(CommandEnum.CHAT_WS, secondId, CoopMessages.CoopUserInputMessage.ProposeCompanyAck(travelName, firstId)),
        Triple(
            CommandEnum.CHAT_WS,
            firstId,
            CoopMessages.CoopUserInputMessage.ResourceDecide(resourcesDecide, secondId)
        ),
        Triple(
            CommandEnum.CHAT_WS,
            secondId,
            CoopMessages.CoopUserInputMessage.ResourceDecideAck(resourcesDecide, firstId)
        ),
        Triple(CommandEnum.CHAT_WS, firstId, CoopMessages.CoopUserInputMessage.StartTravel(travelName))
    )
    gameService.parseCommands(commands)


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
}
