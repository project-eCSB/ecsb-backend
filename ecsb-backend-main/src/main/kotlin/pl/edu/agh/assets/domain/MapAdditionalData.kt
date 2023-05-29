package pl.edu.agh.assets.domain

data class MapAdditionalData(
    val assetId: SavedAssetsId,
    val characterAssetsId: SavedAssetsId,
    val mapAssetDataDto: MapAssetDataDto
)
