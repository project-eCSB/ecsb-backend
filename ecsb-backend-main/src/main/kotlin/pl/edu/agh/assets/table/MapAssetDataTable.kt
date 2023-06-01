package pl.edu.agh.assets.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.utils.intWrapper

object MapAssetDataTable : Table("MAP_ASSET_DATA") {
    val id: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("SAVED_ASSET_ID")
    val dataName = varchar("DATA_NAME", 255)
    val dataValue = varchar("DATA_VALUE", 255)
    val x = integer("X")
    val y = integer("Y")

    data class MapAssetDataRow(
        val id: SavedAssetsId,
        val dataName: String,
        val dataValue: String,
        val x: Int,
        val y: Int
    )

    fun BatchInsertStatement.insertMapAssetDataRow(mapAssetDataRow: MapAssetDataRow) {
        this[MapAssetDataTable.id] = mapAssetDataRow.id
        this[MapAssetDataTable.dataName] = mapAssetDataRow.dataName
        this[MapAssetDataTable.dataValue] = mapAssetDataRow.dataValue
        this[MapAssetDataTable.x] = mapAssetDataRow.x
        this[MapAssetDataTable.y] = mapAssetDataRow.y
    }

    fun getData(mapDataTypes: MapDataTypes) =
        (MapAssetDataTable.dataName eq mapDataTypes.dataName) and (MapAssetDataTable.dataValue eq mapDataTypes.dataValue)
}
