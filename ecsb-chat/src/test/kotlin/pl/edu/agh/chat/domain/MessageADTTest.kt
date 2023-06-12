package pl.edu.agh.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.GameResourceDto
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
                    listOf(
                        GameResourceDto(GameResourceName("bread"), 1),
                        GameResourceDto(GameResourceName("wheel"), 1),
                        GameResourceDto(GameResourceName("cotton"), 1)
                    )
                ),
                PlayerEquipment(
                    2,
                    2,
                    listOf(
                        GameResourceDto(GameResourceName("bread"), 0),
                        GameResourceDto(GameResourceName("wheel"), 0),
                        GameResourceDto(GameResourceName("cotton"), 0)
                    )
                )
            ),
            PlayerId("ez player")
        )
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"tradeBid","tradeBid":{"senderOffer":{"money":1,"time":1,"resources":[{"name":"bread","value":1},{"name":"wheel","value":1},{"name":"cotton","value":1}]},"senderRequest":{"money":2,"time":2,"resources":[{"name":"bread","value":0},{"name":"wheel","value":0},{"name":"cotton","value":0}]}},"receiverId":"ez player"}""".trimMargin(),
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
        val messageADT = MessageADT.SystemInputMessage.MulticastMessage("elo elo message", PlayerId("gracz"))
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"notification/generic","message":"elo elo message","senderId":"gracz"}""",
            serializer
        )
    }
}
