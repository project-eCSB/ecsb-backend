package pl.edu.agh.move.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.messages.domain.MessageSenderData
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
        val testCase = Message(
            MessageSenderData(1),
            MessageADT.PlayerAdded("pl1", 1, 1),
            LocalDateTime.of(2023, 1, 1, 1, 1, 1)
        )
        val serializer = Message.serializer()

        test(
            testCase,
            """{"senderData":{"id":1},"message":{"type":"player_added","id":"pl1","x":1,"y":1},"sentAt":"2023-01-01T01:01:01"}""",
            serializer
        )
    }
}