package pl.edu.agh.assets

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.assets.domain.GameAssets
import pl.edu.agh.assets.domain.SavedAssetsConfig
import pl.edu.agh.assets.service.SavedAssetsService

object SavedAssetsModule {
    fun getKoinSavedAssetsModule(savedAssetsConfig: SavedAssetsConfig, defaultAssets: GameAssets): Module = module {
        single { SavedAssetsService(savedAssetsConfig, defaultAssets) }
    }
}
