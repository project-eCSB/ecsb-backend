package pl.edu.agh.move.domain

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.PlayerId
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

    val playerId = PlayerId("pl1")

    @Test
    fun `test player moved message serializer`() {
        val adt: MessageADT = MessageADT.UserInputMessage.Move(Coordinates(2, 6), Direction.UP)
        val json = """{"type":"move","coords":{"x":2,"y":6},"direction":"up"}"""
        test(adt, json)
    }

    @Test
    fun `test sync request message serializer`() {
        val adt: MessageADT = MessageADT.UserInputMessage.SyncRequest()
        val json = """{"type":"sync_request"}"""
        test(adt, json)
    }

    @Test
    fun `test player added message serializer`() {
        val adt: MessageADT = MessageADT.SystemInputMessage.PlayerAdded(playerId, Coordinates(3, 5), Direction.DOWN)
        val json = """{"type":"player_added","id":"pl1","coords":{"x":3,"y":5},"direction":"down"}"""
        test(adt, json)
    }

    @Test
    fun `test player removed message serializer`() {
        val adt: MessageADT = MessageADT.SystemInputMessage.PlayerRemove(playerId)
        val json = """{"type":"player_remove","id":"pl1"}"""
        test(adt, json)
    }

    @Test
    fun `test player syncing message serializer`() {
        val adt: MessageADT = MessageADT.OutputMessage.PlayersSync(listOf(PlayerPosition(playerId, Coordinates(3, 5), Direction.DOWN_RIGHT)))
        val json = """{"type":"player_syncing","players":[{"id":"pl1","coords":{"x":3,"y":5},"direction":"down-right"}]}"""
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
