package pl.edu.agh.move.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.domain.Message
import pl.edu.agh.moving.domain.Coordinates
import pl.edu.agh.moving.domain.Direction
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
        val testCase = Message<MoveMessageADT>(
            playerId,
            MoveMessageADT.OutputMoveMessage.PlayerMoved(playerId, Coordinates(1, 1), Direction.UP_LEFT),
            LocalDateTime.of(2023, 1, 1, 1, 1, 1)
        )
        val serializer = Message.serializer(MoveMessageADT.serializer())

        test(
            testCase,
            """{"senderId":"elo elo","message":{"type":"player_moved","id":"elo elo","coords":{"x":1,"y":1},"direction":"up-left"},"sentAt":"2023-01-01T01:01:01"}""",
            serializer
        )
    }
}
