package pl.edu.agh.assets.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetDto
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.utils.Domainable
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper
import pl.edu.agh.utils.timestampWithTimeZone

object SavedAssetsTable : Table("SAVED_ASSETS"), Domainable<SavedAssetDto> {
    val id: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("ID").autoIncrement()
    val name: Column<String> = varchar("NAME", 255)
    val path: Column<String> = varchar("PATH", 255)
    val fileType: Column<FileType> = stringWrapper(FileType.toString, FileType.fromString)("FILE_TYPE")
    val createdBy = intWrapper(LoginUserId::value, ::LoginUserId)("CREATED_BY")
    val createdAt = timestampWithTimeZone("CREATED_AT").autoIncrement()

    override val domainColumns: List<Expression<*>> = listOf(id, name, fileType, createdAt)

    override fun toDomain(resultRow: ResultRow): SavedAssetDto = SavedAssetDto(
        resultRow[id],
        resultRow[name],
        resultRow[fileType],
        resultRow[createdAt]
    )
}
