package pl.edu.agh.assets.dao

import arrow.core.Option
import arrow.core.firstOrNone
import org.jetbrains.exposed.sql.*
import pl.edu.agh.assets.domain.*
import pl.edu.agh.assets.table.SavedAssetsTable
import pl.edu.agh.auth.domain.LoginUserId

object SavedAssetsDao {

    fun findByName(path: String): Option<SavedAssetDto> = SavedAssetsTable.select {
        SavedAssetsTable.path eq path
    }.map { SavedAssetsTable.toDomain(it) }.firstOrNone()

    fun insertNewAsset(name: String, createdBy: LoginUserId, fileType: FileType, path: String) =
        SavedAssetsTable.insert {
            it[SavedAssetsTable.name] = name
            it[SavedAssetsTable.createdBy] = createdBy
            it[SavedAssetsTable.path] = path
            it[SavedAssetsTable.fileType] = fileType
        }[SavedAssetsTable.id]

    fun getAllAssets(loginUserId: LoginUserId, fileType: FileType): List<SavedAssetDto> = SavedAssetsTable.select {
        SavedAssetsTable.fileType eq fileType and (SavedAssetsTable.createdBy eq loginUserId)
    }.map { SavedAssetsTable.toDomain(it) }

    fun getAssetById(savedAssetsId: SavedAssetsId): Pair<String, FileType> = SavedAssetsTable.select {
        SavedAssetsTable.id eq savedAssetsId
    }.map { it[SavedAssetsTable.path] to it[SavedAssetsTable.fileType] }.first()
}
