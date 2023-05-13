package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class GameSessionId(val value: Int) {
    companion object {
        fun toName(gameSessionId: GameSessionId): String =
            gameSessionId.value.toString()
    }
}
