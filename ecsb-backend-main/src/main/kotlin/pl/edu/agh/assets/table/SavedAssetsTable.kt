package pl.edu.agh.assets.table

import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetDto
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.utils.Utils.getCol
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper
import pl.edu.agh.utils.timestampWithTimeZone

object SavedAssetsTable : Table("SAVED_ASSETS") {
    val id: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("ID").autoIncrement()
    val name: Column<String> = varchar("NAME", 255)
    val path: Column<String> = varchar("PATH", 255)
    val fileType: Column<FileType> = stringWrapper(FileType.toString, FileType.fromString)("FILE_TYPE")
    val createdBy = intWrapper(LoginUserId::value, ::LoginUserId)("CREATED_BY")
    val createdAt = timestampWithTimeZone("CREATED_AT").autoIncrement()

    fun toDomain(it: ResultRow, alias: Alias<SavedAssetsTable>? = null) = SavedAssetDto(
        it.getCol(alias, id),
        it.getCol(alias, name),
        it.getCol(alias, fileType),
        it.getCol(alias, createdAt)
    )
}
