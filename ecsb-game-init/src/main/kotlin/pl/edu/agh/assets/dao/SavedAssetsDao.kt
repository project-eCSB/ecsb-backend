package pl.edu.agh.assets.dao

import arrow.core.Option
import arrow.core.firstOrNone
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetDto
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.assets.table.SavedAssetsTable
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.utils.toDomain

object SavedAssetsDao {

    fun findByName(path: String): Option<SavedAssetDto> =
        SavedAssetsTable
            .select {
                SavedAssetsTable.path eq path
            }.toDomain(SavedAssetsTable).firstOrNone()

    fun insertNewAsset(name: String, createdBy: LoginUserId, fileType: FileType, path: String): SavedAssetsId =
        SavedAssetsTable.insert {
            it[SavedAssetsTable.name] = name
            it[SavedAssetsTable.createdBy] = createdBy
            it[SavedAssetsTable.path] = path
            it[SavedAssetsTable.fileType] = fileType
        }[SavedAssetsTable.id]

    fun getAllAssets(loginUserId: LoginUserId, fileType: FileType): List<SavedAssetDto> =
        SavedAssetsTable.select {
            SavedAssetsTable.fileType eq fileType and (SavedAssetsTable.createdBy eq loginUserId)
        }.toDomain(SavedAssetsTable)

    fun getAssetById(savedAssetsId: SavedAssetsId): Pair<String, FileType> =
        SavedAssetsTable
            .slice(SavedAssetsTable.path, SavedAssetsTable.fileType)
            .select {
                SavedAssetsTable.id eq savedAssetsId
            }.map { it[SavedAssetsTable.path] to it[SavedAssetsTable.fileType] }.first()
}
