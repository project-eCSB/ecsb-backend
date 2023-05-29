package pl.edu.agh.assets

import io.ktor.server.application.*
import org.koin.dsl.module
import pl.edu.agh.assets.domain.SavedAssetsConfig
import pl.edu.agh.assets.service.SavedAssetsService

object SavedAssetsModule {
    fun Application.getKoinSavedAssetsModule() = module {
        single { SavedAssetsConfig.createFromConfig(this@getKoinSavedAssetsModule) }
        single { SavedAssetsService(get()) }
    }
}
