package pl.edu.agh.clients

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import pl.edu.agh.auth.domain.input.LoginRequest
import pl.edu.agh.auth.domain.output.LoginResponse
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.requests.GameJoinCodeRequest
import pl.edu.agh.game.domain.responses.GameJoinResponse

class GameInitService(val client: HttpClient, val mainUrl: String) {

    suspend fun getGameToken(loginRequest: LoginRequest, gameCode: String): String {
        val loginResponse = client.post("$mainUrl/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }.body<LoginResponse>()

        val loginUserToken = loginResponse.jwtToken

        val gameJoinCodeRequest = GameJoinCodeRequest(gameCode, PlayerId(loginRequest.email))

        val (gameToken, _) = client.post("$mainUrl/getGameToken") {
            bearerAuth(loginUserToken)
            contentType(ContentType.Application.Json)
            setBody(gameJoinCodeRequest)
        }.body<GameJoinResponse>()

        return gameToken
    }
}
