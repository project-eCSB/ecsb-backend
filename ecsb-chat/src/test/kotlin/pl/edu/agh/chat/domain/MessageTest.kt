package pl.edu.agh.chat.domain


import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.PlayerId
import java.time.LocalDateTime
import kotlin.test.junit.JUnitAsserter.assertEquals

class MessageSerializerTest {
    private val format = Json

    private fun <T> test(adt: T, strEquivalent: String, kSerializer: KSerializer<T>) {
        assertEquals(
            "encoded T was not equal to strEquivalent", strEquivalent, format.encodeToString(kSerializer, adt)
        )

        val adt2 = format.decodeFromString(kSerializer, strEquivalent)

        assertEquals("decoded str was not equal to T", adt, adt2)
    }

    @Test
    fun `test Message serializer`() {
        val playerId = PlayerId("elo elo")
        val testCase = Message(
            playerId,
            MessageADT.UnicastMessage("elo elo message", PlayerId("ez player")),
            LocalDateTime.of(2023, 1, 1, 1, 1, 1)
        )
        val serializer = Message.serializer()

        test(
            testCase,
            """{"senderData":"elo elo","message":{"type":"unicast","message":"elo elo message","sendTo":"ez player"},"sentAt":"2023-01-01T01:01:01"}""",
            serializer
        )
    }
}
