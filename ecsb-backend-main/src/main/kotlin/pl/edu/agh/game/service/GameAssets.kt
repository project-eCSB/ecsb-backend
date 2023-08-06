package pl.edu.agh.game.service

import arrow.core.getOrElse
import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.game.domain.`in`.GameInitParameters

@Serializable
data class GameAssets(
    val mapAssetId: SavedAssetsId,
    val tileAssetsId: SavedAssetsId,
    val characterAssetsId: SavedAssetsId,
    val resourceAssetsId: SavedAssetsId
) {
    companion object {
        fun createWithDefault(gameInitParameters: GameInitParameters, defaultAssets: GameAssets): GameAssets {
            val effectiveMapId = gameInitParameters.mapAssetId.getOrElse { defaultAssets.mapAssetId }
            val tileAssetId = gameInitParameters.tileAssetId.getOrElse { defaultAssets.tileAssetsId }
            val characterAssetId = gameInitParameters.characterAssetId.getOrElse { defaultAssets.characterAssetsId }
            val resourceAssetsId = gameInitParameters.resourceAssetsId.getOrElse { defaultAssets.resourceAssetsId }
            return GameAssets(
                mapAssetId = effectiveMapId,
                tileAssetsId = tileAssetId,
                characterAssetsId = characterAssetId,
                resourceAssetsId = resourceAssetsId
            )
        }
    }
}
