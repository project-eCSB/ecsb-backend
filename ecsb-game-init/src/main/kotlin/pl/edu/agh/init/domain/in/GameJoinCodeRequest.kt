package pl.edu.agh.init.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.PlayerId

@Serializable
data class GameJoinCodeRequest(
    val gameCode: String,
    val playerId: PlayerId
)
