package pl.edu.agh.assets.domain

import arrow.core.compose
import pl.edu.agh.utils.lower
import pl.edu.agh.utils.upper

enum class FileType(val suffix: String) {
    CHARACTER_ASSET_FILE("png"),
    TILE_ASSET_FILE("png"),
    RESOURCE_ASSET_FILE("png"),
    PNG("png"), // compat - remove after migration to new fileTypes ^^
    MAP("json");

    companion object {
        val fromString = FileType::valueOf compose String::upper
        val toString = String::lower compose FileType::name::get
    }
}
