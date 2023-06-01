package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable

@Serializable
data class MapAssetDto(
    val mapAsset: SavedAssetDto,
    val characterAsset: SavedAssetDto,
    val tilesAsset: SavedAssetDto
)
