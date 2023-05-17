package pl.edu.agh.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.GameResourceDto
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
            MessageADT.UserInputMessage.TradeMessage.TradeBidMessage(
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
            ),
            LocalDateTime.of(2023, 1, 1, 1, 1, 1)
        )
        val serializer = Message.serializer()

        test(
            testCase,
            """{"senderId":"elo elo","message":{"type":"tradeBid","tradeBid":{"senderOffer":{"money":1,"time":1,"resources":[{"name":"bread","value":1},{"name":"wheel","value":1},{"name":"cotton","value":1}]},"senderRequest":{"money":2,"time":2,"resources":[{"name":"bread","value":0},{"name":"wheel","value":0},{"name":"cotton","value":0}]}},"receiverId":"ez player"},"sentAt":"2023-01-01T01:01:01"}""",
            serializer
        )
    }
}
