package pl.edu.agh.coop.domain

import arrow.core.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.PosInt.Companion.pos
import pl.edu.agh.utils.nonEmptyMapOf

class CoopStatesTest {

    private val travelName = TravelName("Londyn")
    private val secondPlayerId = PlayerId("elo1")

    private fun testCommands(initialStates: CoopStates, finalStates: CoopStates, messages: List<CoopInternalMessages>) {
        val result = messages.fold<CoopInternalMessages, Either<String, CoopStates>>(
            initialStates.right()
        ) { state, nextCommand ->
            state.flatMap {
                it.parseCommand(nextCommand)
            }
        }

        result.onLeft { System.err.println("Not good $it") }

        Assertions.assertEquals(result.getOrNull()!!, finalStates)
    }

    @Test
    fun `simple test case for coop states`() {
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.FindCoop(travelName),
            CoopInternalMessages.SystemInputMessage.FindCoopAck(travelName, secondPlayerId),
            CoopInternalMessages.ResourcesDecideAck(none()),
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck(none()),
            CoopInternalMessages.SystemInputMessage.ResourcesGathered(secondPlayerId),
            CoopInternalMessages.SystemInputMessage.EndOfTravelReady
        )

        val initialState = CoopStates.NoCoopState

        testCommands(initialState, initialState, messages)
    }

    @Test
    fun `it should pass city decide step when other player ack city first`() {
        val initialState = CoopStates.NoCoopState
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.ProposeCoopAck(secondPlayerId),
            CoopInternalMessages.CityVotes(nonEmptyMapOf(travelName to 5.pos).some()),
            CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName),
            CoopInternalMessages.CityVoteAck(travelName)
        )
        val finalState = CoopStates.ResourcesDecide.Passive(
            secondPlayerId,
            travelName,
            none(),
            nonEmptyMapOf(travelName to 5.pos).some()
        )

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass city decide when we ack first`() {
        val initialState = CoopStates.NoCoopState
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.ProposeCoopAck(secondPlayerId),
            CoopInternalMessages.CityVotes(nonEmptyMapOf(travelName to PosInt(5)).some()),
            CoopInternalMessages.CityVoteAck(travelName),
            CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName)
        )
        val finalState = CoopStates.ResourcesDecide.Active(
            secondPlayerId,
            travelName,
            none(),
            nonEmptyMapOf(travelName to PosInt(5)).some()
        )

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass city decide when we ack, second player change votes, mutual ack`() {
        val initialState = CoopStates.NoCoopState
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.ProposeCoopAck(secondPlayerId),
            CoopInternalMessages.CityVotes(nonEmptyMapOf(travelName to PosInt(5)).some()),
            CoopInternalMessages.CityVoteAck(travelName),
            CoopInternalMessages.SystemInputMessage.CityVotes,
            CoopInternalMessages.CityVoteAck(travelName),
            CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName)
        )
        val finalState = CoopStates.ResourcesDecide.Active(
            secondPlayerId,
            travelName,
            none(),
            nonEmptyMapOf(travelName to PosInt(5)).some()
        )

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass city decide when they ack, we change votes, mutual ack`() {
        val initialState = CoopStates.NoCoopState
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.ProposeCoopAck(secondPlayerId),
            CoopInternalMessages.CityVotes(nonEmptyMapOf(travelName to PosInt(5)).some()),
            CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName),
            CoopInternalMessages.CityVotes(nonEmptyMapOf(travelName to PosInt(5)).some()),
            CoopInternalMessages.CityVoteAck(travelName),
            CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName)
        )
        val finalState = CoopStates.ResourcesDecide.Active(
            secondPlayerId,
            travelName,
            none(),
            nonEmptyMapOf(travelName to PosInt(5)).some()
        )

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass resource decide (simple)`() {
        val initialState = CoopStates.ResourcesDecide.Active(secondPlayerId, travelName, none(), none())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.ResourcesDecideAck(none()),
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck(none())
        )
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass resource decide`() {
        val initialState = CoopStates.ResourcesDecide.Passive(secondPlayerId, travelName, none(), none())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck(none()),
            CoopInternalMessages.ResourcesDecideAck(none())
        )
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should be able to renegotiate resources when player starting renegotiation`() {
        val initialState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.RenegotiateResourcesRequest,
            CoopInternalMessages.ResourcesDecideAck(none()),
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck(none())
        )
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should be able to renegotiate resources when second player started renegotiation`() {
        val initialState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemInputMessage.RenegotiateResourcesRequest,
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck(none()),
            CoopInternalMessages.ResourcesDecideAck(none())
        )
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should be able to renegotiate city when player started renegotiation`() {
        val initialState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.RenegotiateCityRequest,
            CoopInternalMessages.CityVotes(nonEmptyMapOf(travelName to 5.pos).some()),
            CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName),
            CoopInternalMessages.CityVoteAck(travelName)
        )
        val finalState = CoopStates.ResourcesDecide.Passive(
            secondPlayerId,
            travelName,
            none(),
            nonEmptyMapOf(travelName to 5.pos).some()
        )

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should be able to renegotiate city when second player started renegotiation`() {
        val initialState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemInputMessage.RenegotiateCityRequest,
            CoopInternalMessages.CityVotes(nonEmptyMapOf(travelName to 5.pos).some()),
            CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName),
            CoopInternalMessages.CityVoteAck(travelName)
        )
        val finalState = CoopStates.ResourcesDecide.Passive(
            secondPlayerId,
            travelName,
            none(),
            nonEmptyMapOf(travelName to 5.pos).some()
        )

        testCommands(initialState, finalState, messages)
    }

}
