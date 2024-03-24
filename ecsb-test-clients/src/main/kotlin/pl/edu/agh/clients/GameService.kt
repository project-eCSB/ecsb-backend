package pl.edu.agh.clients

import io.ktor.client.*
import kotlinx.coroutines.delay
import pl.edu.agh.auth.domain.input.LoginRequest
import pl.edu.agh.auth.service.JWTTokenSimple
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.toNonEmptyMapUnsafe
import kotlin.collections.set

class GameService(
    private val httpClient: HttpClient,
    private val ecsbChatUrlWs: String,
    private val ecsbMain: String,
    private val gameCode: String
) {

    private val credentialsMap = mutableMapOf<PlayerId, JWTTokenSimple>()
    private lateinit var chatWSService: ChatWSService
    private lateinit var gameInitService: GameInitService

    suspend fun start(loginRequest: (String) -> LoginRequest, players: List<Int>): List<PlayerId> {
        gameInitService = GameInitService(httpClient, ecsbMain)
        val playerIds = players.map {
            val firstToken = gameInitService.getGameToken(loginRequest("eloelo1$it@elo.pl"), gameCode)
            val playerId = PlayerId(loginRequest("eloelo1$it@elo.pl").email)
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

    private suspend fun doChatWS(playerId: PlayerId, message: ChatMessageADT.UserInputMessage) {
        chatWSService.sendCommand(playerId, message)
    }

    suspend fun parseCommands(commands: List<Triple<CommandEnum, PlayerId, Any>>) {
        commands.forEach { (commandEnum, playerId, value) ->
            println("doing $commandEnum for $playerId with $value")
            when (commandEnum) {
                CommandEnum.CHAT_WS -> doChatWS(playerId, value as ChatMessageADT.UserInputMessage)
            }

            delay(3000L)
        }
    }
}
