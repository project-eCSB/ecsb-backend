package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable

@Serializable
data class MapAssetView(
    val mapAsset: SavedAssetDto,
    val characterAsset: SavedAssetDto,
    val tilesAsset: SavedAssetDto,
    val mapAssetData: MapAssetDataDto
)
