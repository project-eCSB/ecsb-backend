package pl.edu.agh.assets.domain

import io.ktor.server.application.*
import pl.edu.agh.auth.service.getConfigProperty
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.getLogger

data class SavedAssetsConfig(val url: String) {
    fun getFullPath(name: String, fileType: FileType): String {
        return "$url/$name.${fileType.suffix}"
    }

    companion object {
        fun createFromConfig(application: Application): SavedAssetsConfig {
            val configProperty = Utils.catchPrint(getLogger(Application::class.java)) {
                application.getConfigProperty("saved-assets.url")
            }
            return SavedAssetsConfig(configProperty)
        }
    }
}
