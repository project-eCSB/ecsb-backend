package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.MapDataTypes.Trip
import pl.edu.agh.assets.domain.SavedAssetsId

@Serializable
data class GameInitParameters(
    val classResourceRepresentation: List<GameClassResourceDto>,
    val charactersSpreadsheetUrl: String,
    val gameName: String,
    val mapId: SavedAssetsId,
    val travels: Map<Trip, MoneyRange>
)
