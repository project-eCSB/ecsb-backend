package pl.edu.agh.trade.domain

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.nonEmptyMapOf

class TradeStatesTest {

    private val tradeBid = TradeBid(
        TradePlayerEquipment(
            Money(1),
            nonEmptyMapOf(
                GameResourceName("bread") to 1.nonNeg,
                GameResourceName("wheel") to 1.nonNeg,
                GameResourceName("cotton") to 1.nonNeg
            )
        ),
        TradePlayerEquipment(
            Money(2),
            nonEmptyMapOf(
                GameResourceName("bread") to 0.nonNeg,
                GameResourceName("wheel") to 0.nonNeg,
                GameResourceName("cotton") to 0.nonNeg
            )
        )
    )
    private val myId = PlayerId("me")
    private val secondPlayerId = PlayerId("elo1")
    private val thirdPlayerId = PlayerId("elo2")

    private fun testCommands(
        initialStates: TradeStates,
        finalStates: TradeStates,
        messages: List<TradeInternalMessages>
    ) {
        val result = messages.fold<TradeInternalMessages, Either<String, TradeStates>>(
            initialStates.right()
        ) { state, nextCommand ->
            state.flatMap {
                it.parseCommand(nextCommand).mapLeft { it(PlayerId("player")) }
            }
        }
        Assertions.assertEquals(result.getOrNull()!!, finalStates)
    }

    @Test
    fun `simple test case for trade states`() {
        val messages = listOf<TradeInternalMessages>(
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, secondPlayerId),
            TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem(secondPlayerId),
            TradeInternalMessages.UserInputMessage.TradeBidUser(tradeBid, secondPlayerId),
            TradeInternalMessages.SystemInputMessage.TradeBidAckSystem(secondPlayerId)
        )

        val initialState = TradeStates.NoTradeState

        testCommands(initialState, initialState, messages)
    }

    @Test
    fun `after basic trade accept`() {
        val initialState = TradeStates.NoTradeState
        val messages = listOf<TradeInternalMessages>(
            TradeInternalMessages.UserInputMessage.ProposeTradeAckUser(secondPlayerId)
        )
        val finalState = TradeStates.FirstBidPassive(secondPlayerId)

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `after sending invitation and getting ack`() {
        val initialState = TradeStates.NoTradeState
        val messages = listOf<TradeInternalMessages>(
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, secondPlayerId),
            TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem(secondPlayerId)
        )
        val finalState = TradeStates.FirstBidActive(secondPlayerId)

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `after sending few invitation I am waiting for last one ack`() {
        val initialState = TradeStates.NoTradeState
        val messages = listOf<TradeInternalMessages>(
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, secondPlayerId),
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, thirdPlayerId),
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, secondPlayerId),
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, thirdPlayerId)
        )
        val finalState = TradeStates.WaitingForLastProposal(myId, thirdPlayerId)

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `after sending proposal I get proposal`() {
        val initialState = TradeStates.NoTradeState
        val messages = listOf<TradeInternalMessages>(
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, secondPlayerId),
            TradeInternalMessages.SystemInputMessage.ProposeTradeSystem(thirdPlayerId)
        )
        val finalState = TradeStates.WaitingForLastProposal(myId, secondPlayerId)

        testCommands(initialState, finalState, messages)
    }

    @Test
    fun `after sending proposal I accept proposal`() {
        val initialState = TradeStates.NoTradeState
        val messages = listOf<TradeInternalMessages>(
            TradeInternalMessages.UserInputMessage.ProposeTradeUser(myId, secondPlayerId),
            TradeInternalMessages.SystemInputMessage.ProposeTradeSystem(thirdPlayerId),
            TradeInternalMessages.UserInputMessage.ProposeTradeAckUser(thirdPlayerId)
        )
        val finalState = TradeStates.FirstBidPassive(thirdPlayerId)

        testCommands(initialState, finalState, messages)
    }
}
