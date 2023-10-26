package pl.edu.agh.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import pl.edu.agh.auth.domain.LoginCredentials
import pl.edu.agh.auth.service.JWTTokenSimple
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.toNonEmptyMapUnsafe

class GameService(
    private val httpClient: HttpClient,
    private val ecsbChatUrlHttp: String,
    private val ecsbChatUrlWs: String,
    private val ecsbMain: String,
    private val gameCode: String
) {

    private val credentialsMap = mutableMapOf<PlayerId, JWTTokenSimple>()
    private lateinit var chatWSService: ChatWSService
    private lateinit var gameInitService: GameInitService

    suspend fun start(loginCredentials: (String) -> LoginCredentials, players: List<Int>): List<PlayerId> {
        gameInitService = GameInitService(httpClient, ecsbMain)
        val playerIds = players.map {
            val firstToken = gameInitService.getGameToken(loginCredentials("eloelo1$it@elo.pl"), gameCode)
            val playerId = PlayerId(loginCredentials("eloelo1$it@elo.pl").email)
            credentialsMap[playerId] = firstToken
            playerId
        }
        chatWSService = ChatWSService(
            httpClient,
            ecsbChatUrlWs,
            credentialsMap.toNonEmptyMapUnsafe()
        )
        chatWSService.start()
        return playerIds
    }

    private suspend fun doProduction(playerId: PlayerId, amount: PosInt) {
        val gameToken = credentialsMap[playerId]!!
        val status = httpClient.post("$ecsbChatUrlHttp/production") {
            bearerAuth(gameToken)
            contentType(ContentType.Application.Json)
            setBody(amount)
        }.status
        println(status)
    }

    private suspend fun doTravel(playerId: PlayerId, travelName: TravelName) {
        val gameToken = credentialsMap[playerId]!!
        val status = httpClient.post("$ecsbChatUrlHttp/travel") {
            bearerAuth(gameToken)
            contentType(ContentType.Application.Json)
            setBody(travelName)
        }.status
        println(status)
    }

    private suspend fun doChatWS(playerId: PlayerId, message: ChatMessageADT.UserInputMessage) {
        chatWSService.sendCommand(playerId, message)
    }

    suspend fun parseCommands(commands: List<Triple<CommandEnum, PlayerId, Any>>) {
        commands.forEach { (commandEnum, playerId, value) ->
            println("doing $commandEnum for $playerId with $value")
            when (commandEnum) {
                CommandEnum.PRODUCTION -> doProduction(playerId, value as PosInt)
                CommandEnum.TRAVEL -> doTravel(playerId, value as TravelName)
                CommandEnum.CHAT_WS -> doChatWS(playerId, value as ChatMessageADT.UserInputMessage)
            }

            delay(3000L)
        }
    }
}
