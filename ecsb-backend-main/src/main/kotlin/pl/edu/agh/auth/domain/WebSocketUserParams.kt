package pl.edu.agh.auth.domain

import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId

data class WebSocketUserParams(val playerId: PlayerId, val gameSessionId: GameSessionId)
