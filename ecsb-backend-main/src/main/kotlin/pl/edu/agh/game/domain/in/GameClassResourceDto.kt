package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.AssetNumber

@Serializable
data class GameClassResourceDto(val gameClassName: GameClassName, val classAsset: AssetNumber, val gameResourceName: GameResourceName, val resourceAsset: AssetNumber)
