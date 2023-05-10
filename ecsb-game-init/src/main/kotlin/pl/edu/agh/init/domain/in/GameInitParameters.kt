package pl.edu.agh.init.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.game.domain.SessionClassDto

@Serializable
data class GameInitParameters(
    val classRepresentation: Map<GameClassName, SessionClassDto>,
    val charactersSpreadsheetUrl: String,
    val gameName: String
)
