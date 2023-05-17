package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable

@Serializable
data class GameInitParameters(
    val classResourceRepresentation: List<GameClassResourceDto>,
    val charactersSpreadsheetUrl: String,
    val gameName: String
)
