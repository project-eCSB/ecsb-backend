package pl.edu.agh.game.service

import arrow.core.compose
import arrow.core.curried
import io.ktor.server.application.*
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.utils.Utils.getPrefixedField

data class GameAssets(
    val mapAssetId: SavedAssetsId,
    val tileAssetsId: SavedAssetsId,
    val characterAssetsId: SavedAssetsId,
    val resourceAssetsId: SavedAssetsId
) {
    companion object {
        private val getPrefixedField = { application: Application, prefix: String, mainName: String ->
            getPrefixedField(
                application,
                ::SavedAssetsId compose { it.toInt() },
                prefix,
                mainName
            )
        }.curried()

        fun Application.getDefaultAssets(): GameAssets {
            val getField = getPrefixedField(this)("default-assets")

            return GameAssets(
                mapAssetId = getField("map-asset-id"),
                tileAssetsId = getField("tile-asset-id"),
                characterAssetsId = getField("character-asset-id"),
                resourceAssetsId = getField("resource-asset-id")
            )
        }
    }
}
