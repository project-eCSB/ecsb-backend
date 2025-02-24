package pl.edu.agh.assets.dao

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.raise.option
import arrow.core.toNonEmptyListOrNone
import arrow.core.toOption
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.assets.domain.MapAssetDto
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.assets.table.MapAssetDataTable
import pl.edu.agh.assets.table.MapAssetDataTable.insertMapAssetDataRow
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.moving.domain.Coordinates

object MapAssetDao {

    fun saveMapAdditionalData(id: SavedAssetsId, mapAssetDto: MapAssetDto) {
        val dataToInsert = listOf(
            MapAssetDataTable.MapAssetDataRow(
                id,
                MapDataTypes.StartingPoint.dataName,
                MapDataTypes.StartingPoint.dataValue,
                mapAssetDto.startingPoint.x,
                mapAssetDto.startingPoint.y
            )
        )

        val travelDataToInsert = listOf(
            mapAssetDto.highLevelTravels.toList().map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Travel.High)
            },
            mapAssetDto.mediumLevelTravels.toList().map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Travel.Medium)
            },
            mapAssetDto.lowLevelTravels.toList().map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Travel.Low)
            }
        ).flatten()

        val professionDataToInsert = mapAssetDto.professionWorkshops.flatMap { (className, coordinates) ->
            coordinates.map {
                coordsToMapAssetDataRow(it, id, MapDataTypes.Workshop(className))
            }
        }

        MapAssetDataTable.batchInsert(listOf(dataToInsert, travelDataToInsert, professionDataToInsert).flatten()) {
            insertMapAssetDataRow(it)
        }
    }

    fun findMapConfig(savedAssetsId: SavedAssetsId): Option<MapAssetDto> = option {
        val mapAssetDataDto = MapAssetDataTable.slice(
            MapAssetDataTable.dataName,
            MapAssetDataTable.dataValue,
            MapAssetDataTable.y,
            MapAssetDataTable.x
        ).select { MapAssetDataTable.id eq savedAssetsId }
            .groupBy(
                { MapDataTypes.fromDB(it[MapAssetDataTable.dataName], it[MapAssetDataTable.dataValue]) },
                { Coordinates(it[MapAssetDataTable.x], it[MapAssetDataTable.y]) }
            )

        val lowLevelTravels =
            mapAssetDataDto[MapDataTypes.Travel.Low].toOption().flatMap { it.toNonEmptyListOrNone() }.bind()
        val mediumLevelTravels =
            mapAssetDataDto[MapDataTypes.Travel.Medium].toOption().flatMap { it.toNonEmptyListOrNone() }.bind()
        val highLevelTravels =
            mapAssetDataDto[MapDataTypes.Travel.High].toOption().flatMap { it.toNonEmptyListOrNone() }.bind()
        val startingPoint = mapAssetDataDto[MapDataTypes.StartingPoint].toOption().flatMap { it.firstOrNone() }.bind()
        val professionWorkshops = mapAssetDataDto.filter { (type, _) -> type is MapDataTypes.Workshop }
            .mapKeys { (type, _) -> GameClassName(type.dataValue) }

        MapAssetDto(
            lowLevelTravels = lowLevelTravels,
            mediumLevelTravels = mediumLevelTravels,
            highLevelTravels = highLevelTravels,
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
