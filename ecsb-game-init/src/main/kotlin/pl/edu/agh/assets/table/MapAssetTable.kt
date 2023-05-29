package pl.edu.agh.assets.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.utils.intWrapper

object MapAssetTable : Table("MAP_ASSET") {
    val id: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("SAVED_ASSET_ID")
    val characterSpreadsheetId: Column<SavedAssetsId> =
        intWrapper(SavedAssetsId::value, ::SavedAssetsId)("CHARACTER_SPREADSHEET_ID")
    val tilesSpreadsheetId: Column<SavedAssetsId> =
        intWrapper(SavedAssetsId::value, ::SavedAssetsId)("TILES_SPREADSHEET_ID")
}
