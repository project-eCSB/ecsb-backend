package pl.edu.agh.assets.dao

import arrow.core.Option
import arrow.core.firstOrNone
import org.jetbrains.exposed.sql.*
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.MapAssetDto
import pl.edu.agh.assets.domain.SavedAssetDto
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.assets.table.MapAssetTable
import pl.edu.agh.assets.table.SavedAssetsTable
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.Coordinates

object SavedAssetsDao {

    fun findByName(path: String): Option<SavedAssetDto> =
        SavedAssetsTable.select {
            SavedAssetsTable.path eq path
        }.map { SavedAssetsTable.toDomain(it) }.firstOrNone()

    fun insertNewAsset(name: String, createdBy: LoginUserId, fileType: FileType, path: String) =
        SavedAssetsTable.insert {
            it[SavedAssetsTable.name] = name
            it[SavedAssetsTable.createdBy] = createdBy
            it[SavedAssetsTable.path] = path
            it[SavedAssetsTable.fileType] = fileType
        }[SavedAssetsTable.id]

    fun getAllAssets(loginUserId: LoginUserId, fileType: FileType): List<SavedAssetDto> =
        SavedAssetsTable.select {
            SavedAssetsTable.fileType eq fileType and (SavedAssetsTable.createdBy eq loginUserId)
        }.map { SavedAssetsTable.toDomain(it) }

    fun getAssetById(savedAssetsId: SavedAssetsId): Pair<String, FileType> =
        SavedAssetsTable.select {
            SavedAssetsTable.id eq savedAssetsId
        }.map { it[SavedAssetsTable.path] to it[SavedAssetsTable.fileType] }.first()

    fun findMapConfig(savedAssetsId: SavedAssetsId): Option<MapAssetDto> {
        val mapSavedAssetsTable = SavedAssetsTable.alias("MAP_SAVED_ASSETS")
        val imageSavedAssetsTable = SavedAssetsTable.alias("IMAGE_SAVED_ASSETS")
        val characterSavedAssetsTable = SavedAssetsTable.alias("CHAR_SAVED_ASSETS")

        return mapSavedAssetsTable.join(MapAssetTable, JoinType.INNER) {
            mapSavedAssetsTable[SavedAssetsTable.id] eq MapAssetTable.id
        }.join(characterSavedAssetsTable, JoinType.INNER) {
            MapAssetTable.characterSpreadsheetId eq characterSavedAssetsTable[SavedAssetsTable.id]
        }.join(imageSavedAssetsTable, JoinType.INNER) {
            MapAssetTable.tilesSpreadsheetId eq imageSavedAssetsTable[SavedAssetsTable.id]
        }.select {
            mapSavedAssetsTable[SavedAssetsTable.id] eq savedAssetsId
        }.map {
            MapAssetDto(
                mapAsset = SavedAssetsTable.toDomain(it, mapSavedAssetsTable),
                characterAsset = SavedAssetsTable.toDomain(it, characterSavedAssetsTable),
                tilesAsset = SavedAssetsTable.toDomain(it, imageSavedAssetsTable),
                startingPosition = Coordinates(
                    x = it[MapAssetTable.startingX],
                    y = it[MapAssetTable.startingY]
                )
            )
        }.firstOrNone()
    }
}
