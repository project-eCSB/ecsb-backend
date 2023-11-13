package pl.edu.agh.auth.domain

import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId

data class WebSocketUserParams(
    val loginUserId: LoginUserId,
    val playerId: PlayerId,
    val gameSessionId: GameSessionId,
    val className: GameClassName,
    val gameValid: Boolean
)
