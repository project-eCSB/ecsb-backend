package pl.edu.agh.assets.domain

data class SavedAssetsConfig(val url: String) {
    fun getFullPath(name: String, fileType: FileType): String = "$url/$name.${fileType.suffix}"
}
