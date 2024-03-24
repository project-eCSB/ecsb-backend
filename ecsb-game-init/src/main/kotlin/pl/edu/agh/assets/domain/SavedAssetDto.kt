package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable

@Serializable
data class SavedAssetDto(
    val id: SavedAssetsId,
    val name: String
)
