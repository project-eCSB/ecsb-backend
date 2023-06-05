package pl.edu.agh.assets.dao

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.raise.option
import arrow.core.toNonEmptyListOrNone
import arrow.core.toOption
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.assets.domain.MapAssetDataDto
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.assets.table.MapAssetDataTable
import pl.edu.agh.assets.table.MapAssetDataTable.insertMapAssetDataRow
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameClassName

object MapAssetDao {

    fun saveMapAdditionalData(id: SavedAssetsId, mapAssetDataDto: MapAssetDataDto) {
        val dataToInsert = listOf(
            MapAssetDataTable.MapAssetDataRow(
                id,
                MapDataTypes.StartingPoint.dataName,
                MapDataTypes.StartingPoint.dataValue,
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

    fun findMapConfig(savedAssetsId: SavedAssetsId): Option<MapAssetDataDto> = option {
        val mapAssetDataDto = MapAssetDataTable.select { MapAssetDataTable.id eq savedAssetsId }
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
        val startingPoint = mapAssetDataDto[MapDataTypes.StartingPoint].toOption().flatMap { it.firstOrNone() }.bind()
        val professionWorkshops = mapAssetDataDto.filter { (type, _) -> type is MapDataTypes.Workshop }
            .mapKeys { (type, _) -> GameClassName(type.dataValue) }

        MapAssetDataDto(
            lowLevelTrips = lowLevelTrips,
            mediumLevelTrips = mediumLevelTrips,
            highLevelTrips = highLevelTrips,
            professionWorkshops = professionWorkshops,
            startingPoint = startingPoint
        )
    }

    private fun coordsToMapAssetDataRow(
        coords: Coordinates,
        id: SavedAssetsId,
        mapAssetTypes: MapDataTypes
    ): MapAssetDataTable.MapAssetDataRow =
        MapAssetDataTable.MapAssetDataRow(id, mapAssetTypes.dataName, mapAssetTypes.dataValue, coords.x, coords.y)
}
