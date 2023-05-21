package pl.edu.agh.assets.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.utils.intWrapper

object MapAssetTable : Table("MAP_ASSET") {
    val id: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("SAVED_ASSET_ID")
    val startingX: Column<Int> = integer("STARTING_X")
    val startingY: Column<Int> = integer("STARTING_Y")
    val characterSpreadsheetId: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("CHARACTER_SPREADSHEET_ID")
    val tilesSpreadsheetId: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("TILES_SPREADSHEET_ID")

    fun toDomain(row: ResultRow): Pair<SavedAssetsId, Coordinates> =
        row[MapAssetTable.id] to Coordinates(row[MapAssetTable.startingX], row[MapAssetTable.startingY])
}
