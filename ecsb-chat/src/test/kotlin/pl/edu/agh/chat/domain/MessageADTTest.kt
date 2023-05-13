package pl.edu.agh.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.PlayerId
import kotlin.test.junit.JUnitAsserter.assertEquals

class MessageADTTest {
    private val format = Json

    private fun <T> test(adt: T, strEquivalent: String, kSerializer: KSerializer<T>) {
        assertEquals(
            "encoded T was not equal to strEquivalent", strEquivalent, format.encodeToString(kSerializer, adt)
        )

        val adt2 = format.decodeFromString(kSerializer, strEquivalent)

        assertEquals("decoded str was not equal to T", adt, adt2)
    }

    @Test
    fun `test MessageADT Unicast serializer`() {
        val messageADT = MessageADT.UnicastMessage("elo elo message", PlayerId("ez player"))
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"unicast","message":"elo elo message","sendTo":"ez player"}""",
            serializer
        )
    }

    @Test
    fun `test MessageADT Multicast serializer`() {
        val messageADT = MessageADT.MulticastMessage("elo elo message")
        val serializer = MessageADT.serializer()

        test(
            messageADT,
            """{"type":"multicast","message":"elo elo message"}""",
            serializer
        )
    }
}
