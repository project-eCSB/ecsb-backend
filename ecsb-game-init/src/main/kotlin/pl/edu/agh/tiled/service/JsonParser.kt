package pl.edu.agh.tiled.service

import arrow.core.*
import arrow.core.raise.either
import kotlinx.serialization.json.*
import pl.edu.agh.assets.domain.MapAssetDataDto
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.moving.domain.Coordinates
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.tiled.domain.PropertiesData
import pl.edu.agh.tiled.domain.Tile
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Utils.flatTraverse

typealias ErrorOr<R> = Either<WrongDataFormatException, R>

object JsonParser {

    private val logger by LoggerDelegate()

    private const val PROFESSION_TILE_NAME = "profession"
    private const val TRAVEL_TILE_NAME = "travel"
    private const val SPECIAL_TILE_NAME = "special"
    private const val LOW_RISK_VALUE = "low"
    private const val MEDIUM_RISK_VALUE = "medium"
    private const val HIGH_RISK_VALUE = "high"
    private const val SPAWN_TILE_VALUE = "spawn"
    private const val SECRET_TILE_VALUE = "secret"

    fun parse(mapData: String): ErrorOr<MapAssetDataDto> = either {
        logger.info("Parsing $mapData")
        val json = getMapJson(mapData).bind()
        val tiles = getTilesFromString(json).bind()
        val layers = getLayers(json).bind()
        val mapWidth = json.getIntParam("width").bind()
        val specialTiles = getSpecialTilesFromAllTiles(tiles).bind()

        val professionTilesIdsMap = getSpecialTilesMappedIdsByName(specialTiles, PROFESSION_TILE_NAME)
        val travelTilesIdsLowRisk = getSpecialTilesIdsByName(specialTiles, TRAVEL_TILE_NAME, LOW_RISK_VALUE)
        val travelTilesIdsMediumRisk = getSpecialTilesIdsByName(specialTiles, TRAVEL_TILE_NAME, MEDIUM_RISK_VALUE)
        val travelTilesIdsHighRisk = getSpecialTilesIdsByName(specialTiles, TRAVEL_TILE_NAME, HIGH_RISK_VALUE)
//        val secretTilesIds = getSpecialTilesIdsByName(specialTiles, SPECIAL_TILE_NAME, SECRET_TILE_VALUE)
        val spawnTilesIds = getSpecialTilesIdsByName(specialTiles, SPECIAL_TILE_NAME, SPAWN_TILE_VALUE)

        val professionCoordsMap =
            professionTilesIdsMap.mapKeys { (key, _) -> GameClassName(key) }.mapValues { (key, value) ->
                getCoordinatesForSpecialTiles(
                    MapDataTypes.Workshop(key),
                    layers,
                    value,
                    mapWidth
                )
            }.toList().filterNot { it.second.isLeft() }.traverse { (key, value) -> value.map { key to it } }
                .map { it.toMap().mapValues { (_, values) -> values.toList() } }.bind()
        val travelCoordsLowRisk =
            getCoordinatesForSpecialTiles(MapDataTypes.Travel.Low, layers, travelTilesIdsLowRisk, mapWidth).bind()
        val travelCoordsMediumRisk =
            getCoordinatesForSpecialTiles(MapDataTypes.Travel.Medium, layers, travelTilesIdsMediumRisk, mapWidth).bind()
        val travelCoordsHighRisk =
            getCoordinatesForSpecialTiles(MapDataTypes.Travel.High, layers, travelTilesIdsHighRisk, mapWidth).bind()
//        val secretCoords = getCoordinatesForSpecialTiles(MapDataTypes.Travel.Low, layers, secretTilesIds, mapWidth).bind()

        val spawnCoordsList =
            getCoordinatesForSpecialTiles(MapDataTypes.StartingPoint, layers, spawnTilesIds, mapWidth).bind()
        val spawnCoords = when (spawnCoordsList.size) {
            0 -> Either.Left(WrongDataFormatException.NoSpawnCoords)
            1 -> Either.Right(spawnCoordsList.head)
            else -> Either.Left(WrongDataFormatException.TooManySpawnCoords)
        }.bind()

        MapAssetDataDto(
            lowLevelTravels = travelCoordsLowRisk,
            mediumLevelTravels = travelCoordsMediumRisk,
            highLevelTravels = travelCoordsHighRisk,
            professionWorkshops = professionCoordsMap,
            startingPoint = spawnCoords
        )
    }

    private fun getMapJson(mapData: String): ErrorOr<JsonObject> = kotlin.runCatching {
        Json.parseToJsonElement(mapData).jsonObject
    }.fold({ it.right() }, { WrongDataFormatException.WrongMapFormat.left() })

    private fun getLayers(json: JsonObject): ErrorOr<List<List<Int>>> = either {
        val layersArray = json.safeArray("layers").bind()

        layersArray.traverse {
            either {
                val itt = it.safeObject().bind()
                val dataArray = itt.safeArray("data").bind()

                dataArray.mapNotNull { jsonElement ->
                    jsonElement.toString().toIntOrNull()
                }
            }
        }.bind()
    }

    private fun getTilesFromString(json: JsonObject): ErrorOr<List<JsonElement>> = either {
        val tileSetsArray = json.safeArray("tilesets").bind()

        tileSetsArray.flatTraverse {
            either {
                val obj = it.safeObject().bind()
                val tilesArray = obj.safeArray("tiles").bind()

                tilesArray
            }
        }.bind()
    }

    private fun PropertiesData.hasGEProperty(): Boolean =
        this is PropertiesData.BooleanProperty && this.name.contains("ge_")

    private fun getSpecialTilesFromAllTiles(tiles: List<JsonElement>): ErrorOr<List<Tile>> = tiles.traverse {
        Either.catch { Json.decodeFromJsonElement(Tile.serializer(), it) }
            .mapLeft { WrongDataFormatException.WrongMapFormat }
    }.map {
        it.filter { tile ->
            (tile.properties.any { property -> !property.hasGEProperty() })
        }
    }

    private fun getSpecialTilesMappedIdsByName(
        tiles: List<Tile>,
        name: String
    ): Map<String, Set<Int>> = tiles.mapNotNull { tile ->
        tile.properties.find {
            it is PropertiesData.StringProperty && it.name == name
        }?.let {
            tile.id to (it as PropertiesData.StringProperty).value
        }
    }.groupBy({ it.second }, { it.first.toInt() + 1 }).mapValues { (_, values) -> values.toSet() }

    private fun getSpecialTilesIdsByName(
        tiles: List<Tile>,
        name: String,
        value: String
    ): Set<Int> = tiles.filter { tile ->
        tile.properties.any {
            it is PropertiesData.StringProperty && it.name == name && it.value == value
        }
    }.map { it.id.toInt() + 1 } // layer tiles indexing offset
        .toSet()

    private fun getCoordinatesForSpecialTiles(
        mapDataTypes: MapDataTypes,
        layers: List<List<Int>>,
        tileIds: Set<Int>,
        width: Int
    ): ErrorOr<NonEmptyList<Coordinates>> = layers.flatMap { layer ->
        layer.withIndex().filter {
            tileIds.contains(it.value)
        }.map {
            Coordinates(it.index % width, it.index / width)
        }
    }.toNonEmptyListOrNone().toEither { WrongDataFormatException.NoCoords(mapDataTypes) }
}

private fun JsonObject.getIntParam(name: String): ErrorOr<Int> =
    this[name].toString().toIntOrNull().toOption().toEither { WrongDataFormatException.FieldNonExistent(name) }

private fun JsonObject.safeArray(name: String): ErrorOr<List<JsonElement>> =
    Option.fromNullable(this[name]).toEither { WrongDataFormatException.FieldNonExistent(name) }
        .flatMap { Either.catch { it.jsonArray }.mapLeft { WrongDataFormatException.WrongMapFormat } }

private fun JsonElement.safeObject(): ErrorOr<JsonObject> =
    Either.catch { this.jsonObject }.mapLeft { WrongDataFormatException.WrongMapFormat }

sealed class WrongDataFormatException {

    abstract fun message(): String

    data class FieldNonExistent(val field: String) : WrongDataFormatException() {
        override fun message(): String = "Field $field does not exist in json"
    }

    object WrongMapFormat : WrongDataFormatException() {
        override fun message(): String = "Incorrect format of the map"
    }

    object NoSpawnCoords : WrongDataFormatException() {
        override fun message(): String = "There is no spawn point set"
    }

    object TooManySpawnCoords : WrongDataFormatException() {
        override fun message(): String = "There is too many spawn points set"
    }

    data class NoCoords(val mapDataTypes: MapDataTypes) : WrongDataFormatException() {
        override fun message(): String =
            "Couldn't retrieve coordinates about ${mapDataTypes.dataName} (${mapDataTypes.dataValue})"
    }
}
