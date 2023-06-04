package pl.edu.agh.game.domain.`in`

import arrow.core.none
import arrow.core.some
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.AssetNumber
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.nonEmptyMapOf
import kotlin.test.junit.JUnitAsserter

class GameInitParametersTest {
    private val format = Json

    private fun <T> test(tSerializer: KSerializer<T>, adt: T, strEquivalent: String) {
        JUnitAsserter.assertEquals(
            "encoded adt was not equal to strEquivalent",
            strEquivalent,
            format.encodeToString(tSerializer, adt)
        )

        val adt2 = format.decodeFromString(tSerializer, strEquivalent)

        JUnitAsserter.assertEquals("decoded str was not equal to adt", adt, adt2)
    }

    @Test
    fun `test game init parameters for valid json case`() {
        val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto> = nonEmptyMapOf(
            GameClassName("tkacz") to GameClassResourceDto(
                AssetNumber(1),
                GameResourceName("Koło"),
                AssetNumber(1)
            )
        )
        val gameName: String = "test-gra"
        var mapId: OptionS<SavedAssetsId> = none()
        val travels: NonEmptyMap<MapDataTypes.Trip, Range<Long>> = nonEmptyMapOf(MapDataTypes.Trip.Low to Range(1L, 2L), MapDataTypes.Trip.High to Range(3L, 4L))

        var adt: GameInitParameters = GameInitParameters(classResourceRepresentation, gameName, mapId, travels)

        var json = """{"classResourceRepresentation":[{"key":"tkacz","value":{"classAsset":1,"gameResourceName":"Koło","resourceAsset":1}}],"gameName":"test-gra","mapId":null,"travels":[{"key":"low","value":{"from":1,"to":2}},{"key":"high","value":{"from":3,"to":4}}]}"""
        test(GameInitParameters.serializer(), adt, json)

        mapId = SavedAssetsId(13).some()
        adt = adt.copy(mapId = mapId)
        json = """{"classResourceRepresentation":[{"key":"tkacz","value":{"classAsset":1,"gameResourceName":"Koło","resourceAsset":1}}],"gameName":"test-gra","mapId":13,"travels":[{"key":"low","value":{"from":1,"to":2}},{"key":"high","value":{"from":3,"to":4}}]}"""
        test(GameInitParameters.serializer(), adt, json)
    }

    @Test
    fun `test game init parameters with empty maps`() {
        assertThrows<Throwable> {
            val json = """{"classResourceRepresentation":[],"gameName":"test-gra","mapId":null,"travels":[{"key":"low","value":{"from":1,"to":2}},{"key":"high","value":{"from":3,"to":4}}]}"""
            format.decodeFromString(GameInitParameters.serializer(), json)
        }

        assertThrows<Throwable> {
            val json = """{"classResourceRepresentation":[{"key":"tkacz","value":{"classAsset":1,"gameResourceName":"Koło","resourceAsset":1}}],"gameName":"test-gra","mapId":null,"travels":[]}"""
            format.decodeFromString(GameInitParameters.serializer(), json)
        }
    }
}
