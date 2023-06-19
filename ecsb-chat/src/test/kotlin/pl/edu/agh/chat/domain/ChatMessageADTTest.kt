package pl.edu.agh.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.trade.domain.TradeBid
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
        val messageADT = ChatMessageADT.UserInputMessage.TradeMessage.TradeBidMessage(
            TradeBid(
                PlayerEquipment(
                    1.nonNeg,
                    1.nonNeg,
                    nonEmptyMapOf(
                        GameResourceName("bread") to 1.nonNeg,
                        GameResourceName("wheel") to 1.nonNeg,
                        GameResourceName("cotton") to 1.nonNeg
                    )
                ),
                PlayerEquipment(
                    2.nonNeg,
                    2.nonNeg,
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
            """{"type":"tradeBid","tradeBid":{"senderOffer":{"money":1,"time":1,"resources":[{"key":"bread","value":1},{"key":"wheel","value":1},{"key":"cotton","value":1}]},"senderRequest":{"money":2,"time":2,"resources":[{"key":"bread","value":0},{"key":"wheel","value":0},{"key":"cotton","value":0}]}},"receiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Start Trade serializer`() {
        val messageADT = ChatMessageADT.UserInputMessage.TradeMessage.TradeStartMessage(
            PlayerId("ez player")
        )
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            """{"type":"tradeStart","receiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Cancel Trade serializer`() {
        val messageADT = ChatMessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage(
            PlayerId("ez player")
        )
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            """{"type":"tradeCancel","receiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Multicast serializer`() {
        val messageADT = ChatMessageADT.SystemInputMessage.MulticastMessage("elo elo message", PlayerId("gracz"))
        val serializer = ChatMessageADT.serializer()

        test(
            messageADT,
            """{"type":"notification/generic","message":"elo elo message","senderId":"gracz"}""",
            serializer
        )
    }
}
