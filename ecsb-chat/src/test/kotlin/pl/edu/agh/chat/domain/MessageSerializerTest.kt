package pl.edu.agh.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerEquipmentView
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradePlayerEquipment
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.nonEmptyMapOf
import java.time.LocalDateTime
import kotlin.test.junit.JUnitAsserter.assertEquals

class MessageSerializerTest {
    private val format = Json

    private fun <T> test(adt: T, strEquivalent: String, kSerializer: KSerializer<T>) {
        assertEquals(
            "encoded T was not equal to strEquivalent",
            strEquivalent,
            format.encodeToString(kSerializer, adt)
        )

        val adt2 = format.decodeFromString(kSerializer, strEquivalent)

        assertEquals("decoded str was not equal to T", adt, adt2)
    }

    @Test
    fun `test Message serializer`() {
        val playerId = PlayerId("elo elo")
        val testCase = Message(
            playerId,
            TradeMessages.TradeUserInputMessage.TradeBidMessage(
                TradeBid(
                    TradePlayerEquipment(
                        1.nonNeg,
                        nonEmptyMapOf(
                            GameResourceName("bread") to 1.nonNeg,
                            GameResourceName("wheel") to 1.nonNeg,
                            GameResourceName("cotton") to 1.nonNeg
                        )
                    ),
                    TradePlayerEquipment(
                        2.nonNeg,
                        nonEmptyMapOf(
                            GameResourceName("bread") to 0.nonNeg,
                            GameResourceName("wheel") to 0.nonNeg,
                            GameResourceName("cotton") to 0.nonNeg
                        )
                    )
                ),
                PlayerId("ez player")
            ),
            LocalDateTime.of(2023, 1, 1, 1, 1, 1)
        )
        val serializer = Message.serializer()

        test(
            testCase,
            """{"senderId":"elo elo","message":{"type":"trade/trade_bid","tradeBid":{"senderOffer":{"money":1,"resources":[{"key":"bread","value":1},{"key":"wheel","value":1},{"key":"cotton","value":1}]},"senderRequest":{"money":2,"resources":[{"key":"bread","value":0},{"key":"wheel","value":0},{"key":"cotton","value":0}]}},"receiverId":"ez player"},"sentAt":"2023-01-01T01:01:01"}""",
            serializer
        )
    }

    @Test
    fun `test Player Equipment View serializer`() {
        val view = PlayerEquipmentView(
            PlayerEquipment(
                2.nonNeg,
                2.nonNeg,
                nonEmptyMapOf(
                    GameResourceName("bread") to 1.nonNeg,
                    GameResourceName("wheel") to 1.nonNeg,
                    GameResourceName("cotton") to 1.nonNeg
                )
            ),
            PlayerEquipment(
                1.nonNeg,
                1.nonNeg,
                nonEmptyMapOf(
                    GameResourceName("bread") to 0.nonNeg,
                    GameResourceName("wheel") to 0.nonNeg,
                    GameResourceName("cotton") to 0.nonNeg
                )
            )
        )
        val serializer = PlayerEquipmentView.serializer()

        test(
            view,
            """{"full":{"money":2,"time":2,"resources":[{"key":"bread","value":1},{"key":"wheel","value":1},{"key":"cotton","value":1}]},"shared":{"money":1,"time":1,"resources":[{"key":"bread","value":0},{"key":"wheel","value":0},{"key":"cotton","value":0}]}}""",
            serializer
        )
    }
}
