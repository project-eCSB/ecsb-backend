package pl.edu.agh.move.domain

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.moving.domain.Coordinates
import pl.edu.agh.moving.domain.Direction
import pl.edu.agh.moving.domain.PlayerPosition
import kotlin.test.junit.JUnitAsserter.assertEquals

class MoveMessageADTTest {

    private val format = Json

    private fun test(adt: MoveMessageADT, strEquivalent: String) {
        assertEquals(
            "encoded adt was not equal to strEquivalent",
            strEquivalent,
            format.encodeToString(MoveMessageADT.serializer(), adt)
        )

        val adt2 = format.decodeFromString(MoveMessageADT.serializer(), strEquivalent)

        assertEquals("decoded str was not equal to adt", adt, adt2)
    }

    val playerId = PlayerId("pl1")

    @Test
    fun `test player moved message serializer`() {
        val adt: MoveMessageADT = MoveMessageADT.UserInputMoveMessage.Move(Coordinates(2, 6), Direction.UP)
        val json = """{"type":"move","coords":{"x":2,"y":6},"direction":"up"}"""
        test(adt, json)
    }

    @Test
    fun `test sync request message serializer`() {
        val adt: MoveMessageADT = MoveMessageADT.UserInputMoveMessage.SyncRequest()
        val json = """{"type":"sync_request"}"""
        test(adt, json)
    }

    @Test
    fun `test player added message serializer`() {
        val adt: MoveMessageADT = MoveMessageADT.SystemInputMoveMessage.PlayerAdded(
            playerId,
            Coordinates(3, 5),
            Direction.DOWN,
            GameClassName("ksiądz")
        )
        val json =
            """{"type":"player_added","id":"pl1","coords":{"x":3,"y":5},"direction":"down","className":"ksiądz"}"""
        test(adt, json)
    }

    @Test
    fun `test player removed message serializer`() {
        val adt: MoveMessageADT = MoveMessageADT.SystemInputMoveMessage.PlayerRemove(playerId)
        val json = """{"type":"player_remove","id":"pl1"}"""
        test(adt, json)
    }

    @Test
    fun `test player syncing message serializer`() {
        val adt: MoveMessageADT = MoveMessageADT.OutputMoveMessage.PlayersSync(
            listOf(
                PlayerPositionWithClass(
                    className = GameClassName("ksiądz"),
                    playerPosition = PlayerPosition(playerId, Coordinates(3, 5), Direction.DOWN_RIGHT)
                )
            )
        )
        val json =
            """{"type":"player_syncing","players":[{"className":"ksiądz","playerPosition":{"id":"pl1","coords":{"x":3,"y":5},"direction":"down-right"}}]}"""
        test(adt, json)
    }

    @Test
    fun `test unknown message`() {
        val json = """{"type":"unknown","id":"pl3"}"""
        assertThrows<Exception> {
            format.decodeFromString(MoveMessageADT.serializer(), json)
        }
    }
}
