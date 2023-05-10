package pl.edu.agh.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.ResourceId
import kotlin.test.junit.JUnitAsserter.assertEquals

class MessageADTTest {
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
        val messageADT = MessageADT.UserInputMessage.TradeMessage.TradeBidMessage(
            TradeBid(
                PlayerEquipment(
                    1,
                    1,
                    mapOf(
                        Pair(ResourceId(1), 1),
                        Pair(ResourceId(2), 1),
                        Pair(ResourceId(3), 1)
                    )
                ),
                PlayerEquipment(
                    2,
                    2,
                    mapOf(
                        Pair(ResourceId(1), 0),
                        Pair(ResourceId(2), 0),
                        Pair(ResourceId(3), 0)
                    )
                )
            ),
            PlayerId("ez player")
        )
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"tradeBid","tradeBid":{"senderOffer":{"money":1,"time":1,"products":{"1":1,"2":1,"3":1}},"senderRequest":{"money":2,"time":2,"products":{"1":0,"2":0,"3":0}}},"receiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Start Trade serializer`() {
        val messageADT = MessageADT.UserInputMessage.TradeMessage.TradeStartMessage(
            PlayerId("ez player")
        )
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"tradeStart","receiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Cancel Trade serializer`() {
        val messageADT = MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage(
            PlayerId("ez player")
        )
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"tradeCancel","receiverId":"ez player"}""".trimMargin(),
            serializer
        )
    }

    @Test
    fun `test MessageADT Multicast serializer`() {
        val messageADT = MessageADT.UserInputMessage.MulticastMessage("elo elo message")
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"multicast","message":"elo elo message"}""",
            serializer
        )
    }
}
