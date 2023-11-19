package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable

@Serializable
data class GameResults(val gameSessionName: String, val playersLeaderboard: List<PlayerResult>)
