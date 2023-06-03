package pl.edu.agh.clients

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import pl.edu.agh.auth.domain.LoginCredentials
import pl.edu.agh.auth.domain.LoginUserData
import pl.edu.agh.auth.service.JWTTokenSimple
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerStatus
import pl.edu.agh.game.domain.`in`.GameJoinCodeRequest
import pl.edu.agh.game.domain.out.GameJoinResponse
import pl.edu.agh.game.domain.out.GameSessionView

class GameInitService(val client: HttpClient, val mainUrl: String) {

    suspend fun getGameToken(loginCredentials: LoginCredentials, gameCode: String): JWTTokenSimple {
        val loginUserData = client.post("$mainUrl/login") {
            contentType(ContentType.Application.Json)
            setBody(loginCredentials)
        }.body<LoginUserData>()

        val loginUserToken = loginUserData.jwtToken

        val gameJoinCodeRequest = GameJoinCodeRequest(gameCode, PlayerId(loginCredentials.email))

        val (gameToken, gameSessionId) = client.post("$mainUrl/getGameToken") {
            bearerAuth(loginUserToken)
            contentType(ContentType.Application.Json)
            setBody(gameJoinCodeRequest)
        }.body<GameJoinResponse>()

        val gameSettings = client.get("$mainUrl/settings") { bearerAuth(gameToken) }.body<GameSessionView>()

        val playerStatus = client.get("$mainUrl/gameStatus") { bearerAuth(gameToken) }.body<PlayerStatus>()

        return gameToken
    }

}