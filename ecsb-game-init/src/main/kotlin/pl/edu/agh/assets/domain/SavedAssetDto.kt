package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.InstantSerializer
import java.time.Instant

@Serializable
data class SavedAssetDto(
    val id: SavedAssetsId,
    val name: String,
    val fileType: FileType,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant
)
