package pl.edu.agh.tiled.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.Coordinates

@Serializable
data class ParsedMapData(
    val spawn: Coordinates,
    val professionTiles: Map<String, List<Coordinates>>,
    val travelTilesLowRisk: List<Coordinates>,
    val travelTilesMediumRisk: List<Coordinates>,
    val travelTilesHighRisk: List<Coordinates>,
    val secretTiles: List<Coordinates>
)
