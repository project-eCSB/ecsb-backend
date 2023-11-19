package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.PlayerId

@Serializable
data class GameUserDto(
    val gameSessionId: GameSessionId,
    val playerId: PlayerId,
    val loginUserId: LoginUserId,
    val className: GameClassName,
    val inGame: Boolean
)
