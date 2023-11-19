package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.SavedAssetsId

@Serializable
data class GameAssets(
    val mapAssetId: SavedAssetsId,
    val tileAssetsId: SavedAssetsId,
    val characterAssetsId: SavedAssetsId,
    val resourceAssetsId: SavedAssetsId
)
