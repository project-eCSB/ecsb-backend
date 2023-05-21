package pl.edu.agh.assets.domain

import pl.edu.agh.domain.Coordinates

data class MapAdditionalData(
    val startingPosition: Coordinates,
    val assetId: SavedAssetsId,
    val characterAssetsId: SavedAssetsId
)
