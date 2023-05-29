package pl.edu.agh.assets.dao

import arrow.core.*
import arrow.core.raise.option
import org.jetbrains.exposed.sql.*
import pl.edu.agh.assets.domain.*
import pl.edu.agh.assets.table.MapAssetDataTable
import pl.edu.agh.assets.table.MapAssetDataTable.insertMapAssetDataRow
import pl.edu.agh.assets.table.MapAssetTable
import pl.edu.agh.assets.table.SavedAssetsTable
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameClassName
import kotlin.collections.flatMap
import kotlin.collections.flatten

object MapAssetDao {

    fun saveMapAdditionalData(id: SavedAssetsId, mapAdditionalData: MapAdditionalData) {
        MapAssetTable.insert {
            it[this.id] = id
            it[this.characterSpreadsheetId] = mapAdditionalData.characterAssetsId
            it[this.tilesSpreadsheetId] = mapAdditionalData.assetId
        }

        val mapAssetDataDto = mapAdditionalData.mapAssetDataDto

        val dataToInsert = listOf(
            MapAssetDataTable.MapAssetDataRow(
                id,
                "startingPoint",
                "",
                mapAssetDataDto.startingPoint.x,
                mapAssetDataDto.startingPoint.y
            )
        )

        val tripDataToInsert = listOf(
            mapAssetDataDto.highLevelTrips.toList().map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Trip.High)
            },
            mapAssetDataDto.mediumLevelTrips.toList().map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Trip.Medium)
            },
            mapAssetDataDto.lowLevelTrips.toList().map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Trip.Low)
            }
        ).flatten()

        val professionDataToInsert = mapAssetDataDto.professionWorkshops.flatMap { (className, coordinates) ->
            coordinates.map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Workshop(className))
            }
        }

        MapAssetDataTable.batchInsert(listOf(dataToInsert, tripDataToInsert, professionDataToInsert).flatten()) {
            insertMapAssetDataRow(it)
        }
    }

    fun findMapConfig(savedAssetsId: SavedAssetsId): Option<MapAssetView> {
        val mapSavedAssetsTable = SavedAssetsTable.alias("MAP_SAVED_ASSETS")
        val imageSavedAssetsTable = SavedAssetsTable.alias("IMAGE_SAVED_ASSETS")
        val characterSavedAssetsTable = SavedAssetsTable.alias("CHAR_SAVED_ASSETS")

        return option {
            val mapAssetDto = mapSavedAssetsTable.join(MapAssetTable, JoinType.INNER) {
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
                    tilesAsset = SavedAssetsTable.toDomain(it, imageSavedAssetsTable)
                )
            }.firstOrNone().bind()

            val mapAssetDataDto = MapAssetDataTable
                .select { MapAssetDataTable.id eq savedAssetsId }
                .groupBy(
                    { MapDataTypes.fromDB(it[MapAssetDataTable.dataName], it[MapAssetDataTable.dataValue]) },
                    { Coordinates(it[MapAssetDataTable.x], it[MapAssetDataTable.y]) }
                )

            val lowLevelTrips =
                mapAssetDataDto[MapDataTypes.Trip.Low].toOption().flatMap { it.toNonEmptyListOrNone() }.bind()
            val mediumLevelTrips =
                mapAssetDataDto[MapDataTypes.Trip.Medium].toOption().flatMap { it.toNonEmptyListOrNone() }.bind()
            val highLevelTrips =
                mapAssetDataDto[MapDataTypes.Trip.High].toOption().flatMap { it.toNonEmptyListOrNone() }.bind()
            val startingPoint =
                mapAssetDataDto[MapDataTypes.StartingPoint].toOption().flatMap { it.firstOrNone() }.bind()
            val professionWorkshops =
                mapAssetDataDto.filter { (type, _) -> type is MapDataTypes.Workshop }
                    .mapKeys { (type, _) -> GameClassName(type.dataValue) }

            MapAssetView(
                mapAsset = mapAssetDto.mapAsset,
                characterAsset = mapAssetDto.characterAsset,
                tilesAsset = mapAssetDto.tilesAsset,
                mapAssetData = MapAssetDataDto(
                    lowLevelTrips = lowLevelTrips,
                    mediumLevelTrips = mediumLevelTrips,
                    highLevelTrips = highLevelTrips,
                    professionWorkshops = professionWorkshops,
                    startingPoint = startingPoint
                )
            )
        }
    }

    private fun coordsToMapAssetDataRow(
        coords: Coordinates,
        id: SavedAssetsId,
        mapAssetTypes: MapDataTypes
    ): MapAssetDataTable.MapAssetDataRow =
        MapAssetDataTable.MapAssetDataRow(id, mapAssetTypes.dataName, mapAssetTypes.dataValue, coords.x, coords.y)
}
