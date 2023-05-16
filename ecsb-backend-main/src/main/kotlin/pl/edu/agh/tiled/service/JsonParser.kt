package pl.edu.agh.tiled.service

import arrow.core.*
import kotlinx.serialization.json.*
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.tiled.domain.ParsedMapData

object JsonParser {

    private const val PROFESSION_TILE_NAME = "\"profession\""
    private const val TRAVEL_TILE_NAME = "\"travel\""
    private const val SPECIAL_TILE_NAME = "\"special\""
    private const val LOW_RISK_VALUE = "\"low\""
    private const val MEDIUM_RISK_VALUE = "\"medium\""
    private const val HIGH_RISK_VALUE = "\"high\""
    private const val SPAWN_TILE_VALUE = "\"spawn\""
    private const val SECRET_TILE_VALUE = "\"secret\""

    fun parse(mapData: String): Either<WrongDataFormatException, ParsedMapData> {
        val json = getMapJson(mapData).getOrElse { return Either.Left(WrongDataFormatException.WrongMapFormat) }
        val tiles = getTilesFromString(json).getOrElse { return Either.Left(WrongDataFormatException.FieldNonExistent("tilesets")) }
        val layers = getLayers(json).getOrElse { return Either.Left(WrongDataFormatException.FieldNonExistent("layers")) }
        val mapWidth = json["width"].toString().toIntOrNull().toOption().getOrElse { return Either.Left(WrongDataFormatException.FieldNonExistent("width")) }
        val specialTiles = getSpecialTilesFromAllTiles(tiles)

        val professionTilesIdsMap = getSpecialTilesMappedIdsByName(specialTiles, PROFESSION_TILE_NAME)
        val travelTilesIdsLowRisk = getSpecialTilesIdsByName(specialTiles, TRAVEL_TILE_NAME, LOW_RISK_VALUE)
        val travelTilesIdsMediumRisk = getSpecialTilesIdsByName(specialTiles, TRAVEL_TILE_NAME, MEDIUM_RISK_VALUE)
        val travelTilesIdsHighRisk = getSpecialTilesIdsByName(specialTiles, TRAVEL_TILE_NAME, HIGH_RISK_VALUE)
        val secretTilesIds = getSpecialTilesIdsByName(specialTiles, SPECIAL_TILE_NAME, SECRET_TILE_VALUE)
        val spawnTilesIds = getSpecialTilesIdsByName(specialTiles, SPECIAL_TILE_NAME, SPAWN_TILE_VALUE)

        val professionCoordsMap = professionTilesIdsMap.mapValues { getCoordinatesForSpecialTiles(layers, it.value, mapWidth) }
        val travelCoordsLowRisk = getCoordinatesForSpecialTiles(layers, travelTilesIdsLowRisk, mapWidth)
        val travelCoordsMediumRisk = getCoordinatesForSpecialTiles(layers, travelTilesIdsMediumRisk, mapWidth)
        val travelCoordsHighRisk = getCoordinatesForSpecialTiles(layers, travelTilesIdsHighRisk, mapWidth)
        val secretCoords = getCoordinatesForSpecialTiles(layers, secretTilesIds, mapWidth)
        val spawnCoords = getCoordinatesForSpecialTiles(layers, spawnTilesIds, mapWidth)

        return when (spawnCoords.size) {
            0 -> Either.Left(WrongDataFormatException.NoSpawnCoords)
            1 -> Either.Right(ParsedMapData(spawnCoords[0],
                                            professionCoordsMap,
                                            travelCoordsLowRisk,
                                            travelCoordsMediumRisk,
                                            travelCoordsHighRisk,
                                            secretCoords))
            else -> Either.Left(WrongDataFormatException.TooManySpawnCoords)
        }
    }

    private fun getMapJson(mapData: String): Option<JsonObject> =
        kotlin.runCatching {
            Json.parseToJsonElement(mapData).jsonObject
        }.getOrElse { null }.toOption()

    private fun getLayers(json: JsonObject): Option<List<List<Int>>> =
        json["layers"]?.jsonArray?.map {
            it.jsonObject["data"]?.jsonArray?.map{ jsonElement ->
                jsonElement.toString().toIntOrNull()
            }?.filterIsInstance<Int>()?.toList() ?: emptyList()
        }.toOption()

    private fun getTilesFromString(json: JsonObject): Option<List<JsonElement>> =
        json["tilesets"]?.jsonArray?.flatMap {
            it.jsonObject["tiles"]?.jsonArray ?: emptyList()
        }.toOption()

    private fun getSpecialTilesFromAllTiles(tiles: List<JsonElement>): List<JsonElement> =
        tiles.filter { tile ->
            val properties = tile.jsonObject["properties"]?.jsonArray
            !properties.isNullOrEmpty() && (properties.size > 1 || !properties[0].jsonObject["name"].toString().contains("ge_"))
        }

    private fun getSpecialTilesMappedIdsByName(tiles: List<JsonElement>, name: String): Map<String, List<Int>> =
        tiles.mapNotNull { tile ->
            val properties = tile.jsonObject["properties"]?.jsonArray
            properties?.let { props ->
                val matchingProperty = props.firstOrNull { it.jsonObject["name"].toString() == name}
                matchingProperty?.let {
                    val id = tile.jsonObject["id"].toString().toIntOrNull()
                    val value = it.jsonObject["value"].toString().replace("\"", "")
                    id?.let { tileId -> Pair(tileId, value) }
                }
            }
        }.groupBy { (_, value) -> value }.mapValues { (_, pairs) -> pairs.map { (id, _) -> id + 1 } }

    private fun getSpecialTilesIdsByName(tiles: List<JsonElement>, name: String, value: String): List<Int> =
        tiles.filter {
            val properties = it.jsonObject["properties"]?.jsonArray
            properties?.let {
                val matchingProperty = properties.firstOrNull { property ->
                    property.jsonObject["name"].toString() == name &&
                            property.jsonObject["value"].toString() == value
                }
                matchingProperty?.let { return@filter true }
            }
            return@filter false
        }.map {
            it.jsonObject["id"].toString().toIntOrNull()
        }.filterIsInstance<Int>().map { it + 1 } // layer tiles indexing offset

    private fun getCoordinatesForSpecialTiles(layers: List<List<Int>>, tileIds: List<Int>, width: Int): List<Coordinates> =
        layers.flatMap { layer ->
            layer.withIndex().filter {
                tileIds.contains(it.value)
            }.map {
                Coordinates(it.index % width, it.index / width)
            }
        }

}

sealed class WrongDataFormatException {

    abstract fun message(): String

    data class FieldNonExistent(val field: String): WrongDataFormatException() {
        override fun message(): String =
            "Field $field does not exist in json"
    }

    object WrongMapFormat: WrongDataFormatException() {
        override fun message(): String =
            "Incorrect format of the map"
    }

    object NoSpawnCoords: WrongDataFormatException() {
        override fun message(): String =
            "There is no spawn point set"
    }

    object TooManySpawnCoords: WrongDataFormatException() {
        override fun message(): String =
            "There is too many spawn points set"
    }

}
