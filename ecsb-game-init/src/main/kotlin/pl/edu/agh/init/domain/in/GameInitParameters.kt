package pl.edu.agh.init.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.game.domain.AssetNumber

@Serializable
data class GameInitParameters(
    val classRepresentation: Map<GameClassName, AssetNumber>,
    val charactersSpreadsheetUrl: String,
    val gameName: String
)
