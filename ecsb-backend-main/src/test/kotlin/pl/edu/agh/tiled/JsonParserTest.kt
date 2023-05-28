package pl.edu.agh.tiled

import arrow.core.getOrElse
import arrow.core.toOption
import org.junit.jupiter.api.Test
import pl.edu.agh.tiled.service.JsonParser
import pl.edu.agh.tiled.service.WrongDataFormatException

class JsonParserTest {

    @Test
    fun accurateSpawnAndAmountOfTilesTest() {
        // given
        val path = "/forest_glade.json"

        // when
        val stringFromResources = JsonParser::class.java.getResource(path).toOption().map { it.readText() }
        val data = JsonParser.parse(stringFromResources.getOrElse { "" })

        // then
        assert(data.isRight())
    }

    @Test
    fun mapWithoutSpawnTest() {
        // given
        val path = "/mockMap.json"

        // when
        val stringFromResources = JsonParser::class.java.getResource(path).toOption().map { it.readText() }
        val data = JsonParser.parse(stringFromResources.getOrElse { "" })

        // then
        assert(data.isLeft())
        assert(data.leftOrNull() is WrongDataFormatException.NoSpawnCoords)
    }

    @Test
    fun invalidMapTest() {
        // given
        val path = "/emptyMap.json"

        // when
        val stringFromResources = JsonParser::class.java.getResource(path).toOption().map { it.readText() }
        val data = JsonParser.parse(stringFromResources.getOrElse { "" })

        // then
        assert(data.leftOrNull() is WrongDataFormatException.FieldNonExistent)
        assert(data.isLeft())
    }

    @Test
    fun nonExistentMapTest() {
        // given
        val mapString = ""

        // when
        val data = JsonParser.parse(mapString)

        // then
        assert(data.leftOrNull() is WrongDataFormatException.WrongMapFormat)
        assert(data.isLeft())
    }
}
