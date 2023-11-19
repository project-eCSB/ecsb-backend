package pl.edu.agh.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.edu.agh.moving.domain.Direction

class DirectionTest {
    @Test
    fun `serialization and deserialization should work correctly`() {
        val direction = Direction.UP_LEFT
        val json = Json.encodeToString(direction)
        val deserialized = Json.decodeFromString<Direction>(json)

        Assertions.assertEquals(direction, deserialized)
    }

    @Test
    fun `serialize should produce correct JSON string`() {
        val direction = Direction.DOWN_RIGHT
        val expectedJson = "\"down-right\""
        val json = Json.encodeToString(direction)

        Assertions.assertEquals(expectedJson, json)
    }

    @Test
    fun `deserialize should return DirectionNONE for empty input`() {
        val json = "\"\""
        val deserialized = Json.decodeFromString<Direction>(json)

        Assertions.assertEquals(Direction.NONE, deserialized)
    }

    @Test
    fun `deserialize should return DirectionNONE for invalid input`() {
        val json = "\"invalid_direction\""
        val deserialized = Json.decodeFromString<Direction>(json)

        Assertions.assertEquals(Direction.NONE, deserialized)
    }

    @Test
    fun `all enum values should be unique`() {
        val values = Direction.values().map { it.value }
        val distinctValues = values.distinct()

        Assertions.assertEquals(values.size, distinctValues.size)
    }

    @Test
    fun `serialize and deserialize should be reversible`() {
        val direction = Direction.UP_LEFT
        val json = Json.encodeToString(direction)
        val deserialized = Json.decodeFromString<Direction>(json)
        val serialized = Json.encodeToString(deserialized)

        Assertions.assertEquals(json, serialized)
    }

    @Test
    fun `deserialization of invalid JSON should throw exception`() {
        val json = "{invalid json}"
        assertThrows<Exception> {
            Json.decodeFromString<Direction>(json)
        }
    }
}
