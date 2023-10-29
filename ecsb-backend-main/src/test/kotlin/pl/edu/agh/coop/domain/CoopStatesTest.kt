package pl.edu.agh.coop.domain

import arrow.core.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegFloat.Companion.nonNeg
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

class CoopStatesTest {

    private val myId = PlayerId("twoja_stara")
    private val travelName = TravelName("Londyn")
    private val randomBid = ResourcesDecideValues(
        myId,
        0.5f.nonNeg,
        NonEmptyMap.fromListUnsafe(listOf(GameResourceName("gówno") to 3.nonNeg))
    )
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

    private fun testWrongCommands(initialStates: CoopStates, message: String, messages: List<CoopInternalMessages>) {
        val result = messages.fold<CoopInternalMessages, Either<String, CoopStates>>(
            initialStates.right()
        ) { state, nextCommand ->
            state.flatMap {
                it.parseCommand(nextCommand)
            }
        }

        result.onLeft {
            Assertions.assertEquals(message, it)
        }.onRight {
            System.err.println("Expected error, instead get state: $it")
            Assertions.assertEquals(true, false)
        }
    }

    @Test
    fun `simple test case for coop states`() {
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.UserInputMessage.ProposeCompanyUser(myId, secondPlayerId, travelName),
            CoopInternalMessages.SystemOutputMessage.ProposeCompanyAckSystem(secondPlayerId, myId, travelName),
            CoopInternalMessages.UserInputMessage.ResourcesDecideUser(myId, randomBid, secondPlayerId),
            CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem(secondPlayerId, randomBid, myId),
            CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem(secondPlayerId),
            CoopInternalMessages.UserInputMessage.StartPlanningTravel(myId, travelName)
        )

        val initialState = CoopStates.GatheringResources(myId, travelName, none())
        val finalState = CoopStates.NoCoopState

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass resource decide (simple)`() {
        val initialState = CoopStates.ResourcesDecide.ResourceNegotiatingActive(myId, secondPlayerId, travelName, true)
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser(myId, randomBid, secondPlayerId),
        )
        val finalState =
            CoopStates.GatheringResources(myId, travelName, (secondPlayerId to randomBid).toOption())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `it should pass resource decide`() {
        val initialState =
            CoopStates.ResourcesDecide.ResourceNegotiatingPassive(myId, secondPlayerId, travelName, randomBid, true)
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem(secondPlayerId, randomBid, myId),
        )
        val finalState =
            CoopStates.GatheringResources(myId, travelName, (secondPlayerId to randomBid).toOption())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `simple case for guy outside`() {
        val initialState = CoopStates.NoCoopState
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemOutputMessage.ProposeCompanySystem(secondPlayerId, myId),
            CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser(myId, secondPlayerId, travelName),
            CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem(secondPlayerId, myId),
            CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser(myId, randomBid, secondPlayerId)
        )
        val finalState =
            CoopStates.GatheringResources(myId, travelName, (secondPlayerId to randomBid).toOption())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `simple case for guy joining someone`() {
        val initialState = CoopStates.NoCoopState
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.UserInputMessage.JoinPlanningUser(myId, secondPlayerId),
            CoopInternalMessages.SystemOutputMessage.JoinPlanningAckSystem(secondPlayerId, myId, travelName),
            CoopInternalMessages.UserInputMessage.ResourcesDecideUser(myId, randomBid, secondPlayerId),
            CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem(secondPlayerId, randomBid, myId)
        )
        val finalState =
            CoopStates.GatheringResources(myId, travelName, (secondPlayerId to randomBid).toOption())

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `ending coop with my travel`() {
        val initialState =
            CoopStates.GatheringResources(myId, travelName, (secondPlayerId to randomBid).toOption())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem(myId),
            CoopInternalMessages.UserInputMessage.StartPlanningTravel(myId, travelName)
        )
        val finalState = CoopStates.NoCoopState

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `ending coop with his travel`() {
        val initialState =
            CoopStates.GatheringResources(secondPlayerId, travelName, (myId to randomBid).toOption())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem(myId),
            CoopInternalMessages.SystemOutputMessage.StartTravel(travelName)
        )
        val finalState = CoopStates.NoCoopState

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `error when no coop and receive end of travel ready`() {
        val initialState = CoopStates.GatheringResources(myId, travelName, none())
        val messages = listOf<CoopInternalMessages>(
            CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem(myId),
            CoopInternalMessages.SystemOutputMessage.StartTravel(travelName)
        )

        testWrongCommands(
            initialState,
            "End of travel message not valid while in GatheringResources with nobody",
            messages
        )
    }

//    @Test
//    fun `it should be able to renegotiate resources when player starting renegotiation`() {
//        val initialState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
//        val messages = listOf<CoopInternalMessages>(
//            CoopInternalMessages.RenegotiateResourcesRequest,
//            CoopInternalMessages.ResourcesDecideAck(none()),
//            CoopInternalMessages.SystemOutputMessage.ResourcesDecideAck(none())
//        )
//        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
//
//        testCommands(initialState, finalState, messages)
//    }

//    @Test
//    fun `it should be able to renegotiate resources when second player started renegotiation`() {
//        val initialState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
//        val messages = listOf<CoopInternalMessages>(
//            CoopInternalMessages.SystemOutputMessage.RenegotiateResourcesRequest,
//            CoopInternalMessages.SystemOutputMessage.ResourcesDecideAck(none()),
//            CoopInternalMessages.ResourcesDecideAck(none())
//        )
//        val finalState = CoopStates.ResourcesGathering(secondPlayerId, travelName, none(), none())
//
//        testCommands(initialState, finalState, messages)
//    }
}
