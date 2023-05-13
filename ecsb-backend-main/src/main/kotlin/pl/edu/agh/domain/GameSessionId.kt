package pl.edu.agh.domain

@JvmInline
value class GameSessionId(val value: Int) {
    companion object {
        fun toName(gameSessionId: GameSessionId): String =
            gameSessionId.value.toString()
    }
}
