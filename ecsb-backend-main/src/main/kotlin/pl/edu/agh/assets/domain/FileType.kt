package pl.edu.agh.assets.domain

import arrow.core.compose
import kotlinx.serialization.Serializable
import pl.edu.agh.utils.lower
import pl.edu.agh.utils.upper

@Serializable
enum class FileType(val suffix: String) {
    CHARACTER_ASSET_FILE("png"),
    TILE_ASSET_FILE("png"),
    RESOURCE_ASSET_FILE("png"),
    MAP("json");

    companion object {
        val fromString: (String) -> FileType = FileType::valueOf compose String::upper
        val toString: (FileType) -> String = String::lower compose FileType::name::get
    }
}
