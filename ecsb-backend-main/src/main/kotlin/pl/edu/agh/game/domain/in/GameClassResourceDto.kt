package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.AssetNumber
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.PosInt

@Serializable
data class GameClassResourceDto(
    val classAsset: AssetNumber,
    val gameResourceName: GameResourceName,
    val resourceAsset: AssetNumber,
    val maxProduction: PosInt,
    val unitPrice: PosInt,
    val regenTime: TimestampMillis
)
