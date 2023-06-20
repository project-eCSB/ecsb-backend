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

        Assertions.assertEquals(result.getOrNull()!!, finalStates)
    }

    @Test
    fun `simple test case for coop states`() {
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.FindCoop(travelName),
            CoopInternalMessages.SystemInputMessage.FindCoopAck(travelName, secondPlayerId),
            CoopInternalMessages.SystemInputMessage.ResourcesGathered,
            CoopInternalMessages.StartResourcesDecide,
            CoopInternalMessages.ResourcesDecideAck,
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck,
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
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none())

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
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none())

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
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none())

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
        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass resource decide (simple)`() {
        val initialState = CoopStates.ResourcesGathered(secondPlayerId, travelName)
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.StartResourcesDecide,
            CoopInternalMessages.ResourcesDecideAck,
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck
        )
        val finalState = CoopStates.WaitingForCoopEnd(secondPlayerId)

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass resource decide`() {
        val initialState = CoopStates.ResourcesGathered(secondPlayerId, travelName)
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemInputMessage.StartResourcesPassiveDecide,
            CoopInternalMessages.SystemInputMessage.ResourcesDecideAck,
            CoopInternalMessages.ResourcesDecideAck
        )
        val finalState = CoopStates.WaitingForCoopEnd(secondPlayerId)

        testCommands(initialState, finalState, messages)
    }
}
