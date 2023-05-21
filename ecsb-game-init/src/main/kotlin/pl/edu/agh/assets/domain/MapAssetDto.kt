package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.Coordinates

@Serializable
data class MapAssetDto(
    val mapAsset: SavedAssetDto,
    val characterAsset: SavedAssetDto,
    val tilesAsset: SavedAssetDto,
    val startingPosition: Coordinates
)
