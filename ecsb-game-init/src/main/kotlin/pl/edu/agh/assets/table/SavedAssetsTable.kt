package pl.edu.agh.assets.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetDto
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper
import pl.edu.agh.utils.timestampWithTimeZone

object SavedAssetsTable : Table("SAVED_ASSETS") {
    val id: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("ID").autoIncrement()
    val name: Column<String> = varchar("NAME", 255)
    val path: Column<String> = varchar("PATH", 255)
    val fileType: Column<FileType> = stringWrapper(FileType::name) { FileType.valueOf(it) }("FILE_TYPE")
    val createdBy = intWrapper(LoginUserId::id, ::LoginUserId)("CREATED_BY")
    val createdAt = timestampWithTimeZone("CREATED_AT").autoIncrement()

    fun toDomain(it: ResultRow) = SavedAssetDto(
        it[id],
        it[name],
        it[fileType],
        it[createdAt]
    )
}
