package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable

@Serializable
data class GameAssets(
    val mapAssetId: SavedAssetsId,
    val tileAssetsId: SavedAssetsId,
    val characterAssetsId: SavedAssetsId,
    val resourceAssetsId: SavedAssetsId
)
