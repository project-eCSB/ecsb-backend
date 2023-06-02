package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.AssetNumber

@Serializable
data class GameClassResourceDto(
    val classAsset: AssetNumber,
    val gameResourceName: GameResourceName,
    val resourceAsset: AssetNumber,
    val maxProduction: Int,
    val unitPrice: Int
)
