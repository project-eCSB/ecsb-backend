package pl.edu.agh.game.domain.`in`

import arrow.core.none
import arrow.core.some
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.AssetNumber
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.`in`.TravelParameters
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
                AssetNumber(1),
                5,
                2
            )
        )
        val classResourceRepresentationJson = """[{"key":"tkacz","value":{"classAsset":1,"gameResourceName":"Koło","resourceAsset":1,"maxProduction":5,"unitPrice":2}}]"""
        val gameName = "test-gra"
        var mapId: OptionS<SavedAssetsId> = none()

        val travels: NonEmptyMap<MapDataTypes.Trip, NonEmptyMap<TravelName, TravelParameters>> =
            nonEmptyMapOf(
                MapDataTypes.Trip.Low to nonEmptyMapOf(
                    TravelName("Kraków") to TravelParameters(
                        nonEmptyMapOf(
                            GameResourceName("koło") to 2
                        ),
                        Range(1L, 2L),
                        none()
                    )
                ),
                MapDataTypes.Trip.High to nonEmptyMapOf(
                    TravelName("Warszawa") to TravelParameters(
                        nonEmptyMapOf(
                            GameResourceName("elo") to 1,
                            GameResourceName("elo2") to 3,
                            GameResourceName("elo3") to 7
                        ),
                        Range(7L, 21L),
                        none()
                    )
                )
            )

        val travelsJson = """[{"key":"low","value":[{"key":"Kraków","value":{"assets":[{"key":"koło","value":2}],"moneyRange":{"from":1,"to":2},"time":null}}]},{"key":"high","value":[{"key":"Warszawa","value":{"assets":[{"key":"elo","value":1},{"key":"elo2","value":3},{"key":"elo3","value":7}],"moneyRange":{"from":7,"to":21},"time":null}}]}]"""

        var adt = GameInitParameters(
            classResourceRepresentation = classResourceRepresentation,
            gameName = gameName,
            travels = travels,
            mapAssetId = mapId,
            tileAssetId = SavedAssetsId(4).some(),
            characterAssetId = SavedAssetsId(5).some(),
            resourceAssetsId = SavedAssetsId(6).some()
        )

        var json =
            """{"classResourceRepresentation":$classResourceRepresentationJson,"gameName":"test-gra","travels":$travelsJson,"mapAssetId":null,"tileAssetId":4,"characterAssetId":5,"resourceAssetsId":6}"""
        test(GameInitParameters.serializer(), adt, json)

        mapId = SavedAssetsId(13).some()
        adt = adt.copy(mapAssetId = mapId)
        json =
            """{"classResourceRepresentation":$classResourceRepresentationJson,"gameName":"test-gra","travels":$travelsJson,"mapAssetId":13,"tileAssetId":4,"characterAssetId":5,"resourceAssetsId":6}"""
        test(GameInitParameters.serializer(), adt, json)
    }

    @Test
    fun `test game init parameters with empty maps`() {
        assertThrows<Throwable> {
            val json =
                """{"classResourceRepresentation":[],"gameName":"test-gra","mapId":null,"travels":[{"key":"low","value":{"from":1,"to":2}},{"key":"high","value":{"from":3,"to":4}}]}"""
            format.decodeFromString(GameInitParameters.serializer(), json)
        }

        assertThrows<Throwable> {
            val json =
                """{"classResourceRepresentation":[{"key":"tkacz","value":{"classAsset":1,"gameResourceName":"Koło","resourceAsset":1,"maxProduction":5,"unitPrice":2}}],"gameName":"test-gra","mapId":null,"travels":[]}"""
            format.decodeFromString(GameInitParameters.serializer(), json)
        }
    }
}
