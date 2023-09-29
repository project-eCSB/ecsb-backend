package pl.edu.agh.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.Money
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradePlayerEquipment
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.nonEmptyMapOf
import kotlin.test.junit.JUnitAsserter.assertEquals

class ChatMessageADTTest {
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
    fun `test MessageADT Trade Bid serializer`() {
        val messageADT = TradeMessages.TradeUserInputMessage.TradeBidMessage(
            TradeBid(
                TradePlayerEquipment(
                    Money(1),
                    nonEmptyMapOf(
                        GameResourceName("bread") to 1.nonNeg,
                        GameResourceName("wheel") to 1.nonNeg,
                        GameResourceName("cotton") to 1.nonNeg
                    )
                ),
                TradePlayerEquipment(
                    Money(2),
                    nonEmptyMapOf(
                        GameResourceName("bread") to 0.nonNeg,
                        GameResourceName("wheel") to 0.nonNeg,
                        GameResourceName("cotton") to 0.nonNeg
                    )
                )
            ),
            PlayerId("ez player")
        )
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            """{"type":"trade/trade_bid","tradeBid":{"senderOffer":{"money":1,"resources":[{"key":"bread","value":1},{"key":"wheel","value":1},{"key":"cotton","value":1}]},"senderRequest":{"money":2,"resources":[{"key":"bread","value":0},{"key":"wheel","value":0},{"key":"cotton","value":0}]}},"receiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Start Trade serializer`() {
        val messageADT = TradeMessages.TradeUserInputMessage.ProposeTradeMessage(
            PlayerId("ez player")
        )
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            """{"type":"trade/propose_trade","proposalReceiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Cancel Trade serializer`() {
        val messageADT = TradeMessages.TradeUserInputMessage.CancelTradeAtAnyStage
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            """{"type":"trade/cancel_trade"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Multicast serializer`() {
        val messageADT = ChatMessageADT.SystemOutputMessage.MulticastMessage("elo elo message", PlayerId("gracz"))
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            """{"type":"notification/generic","message":"elo elo message","senderId":"gracz"}""",
            serializer
        )
    }

    @Test
    fun `test MessageADT travel choosing start`() {
        val messageADT = ChatMessageADT.SystemOutputMessage.TravelNotification.TravelChoosingStart(PlayerId("Siema"))
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            "{\"type\":\"notification/choosing/travel/start\",\"playerId\":\"Siema\"}",
            serializer
        )
    }
}
