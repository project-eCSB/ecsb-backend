package pl.edu.agh.move.domain

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.junit.JUnitAsserter.assertEquals

class MessageADTTest {

    private val format = Json

    private fun test(adt: MessageADT, strEquivalent: String) {
        assertEquals(
            "encoded adt was not equal to strEquivalent",
            strEquivalent,
            format.encodeToString(MessageADT.serializer(), adt)
        )

        val adt2 = format.decodeFromString(MessageADT.serializer(), strEquivalent)

        assertEquals("decoded str was not equal to adt", adt, adt2)
    }

    @Test
    fun `test player added message serializer`() {
        val adt: MessageADT = MessageADT.PlayerAdded("pl1", 3, 5)
        val json = """{"type":"player_added","id":"pl1","x":3,"y":5}"""
        test(adt, json)
    }

    @Test
    fun `test player moved message serializer`() {
        val adt: MessageADT = MessageADT.PlayerMoved("pl2", 2, 6)
        val json = """{"type":"player_moved","id":"pl2","x":2,"y":6}"""
        test(adt, json)
    }

    @Test
    fun `test player removed message serializer`() {
        val adt: MessageADT = MessageADT.PlayerRemove("pl3")
        val json = """{"type":"player_remove","id":"pl3"}"""
        test(adt, json)
    }

    @Test
    fun `test unknown message`() {
        val json = """{"type":"unknown","id":"pl3"}"""
        assertThrows<Exception> {
            format.decodeFromString(MessageADT.serializer(), json)
        }
    }
}