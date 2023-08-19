package pl.edu.agh.domain

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradeInternalMessages.UserInputMessage
import pl.edu.agh.trade.domain.TradeStates
import pl.edu.agh.trade.redis.TradeStatesDataConnector
import pl.edu.agh.trade.service.TradeGameEngineService
import java.time.LocalDateTime

class TradeGameEngineTest {
    val tradeStatesDataConnector = mockk<TradeStatesDataConnector>()
    val interactionProducer = mockk<InteractionProducer<ChatMessageADT.SystemInputMessage>>()
    val interactionDataConnector = mockk<InteractionDataConnector>()

    val tradeGameEngineService = TradeGameEngineService(
        tradeStatesDataConnector,
        interactionProducer,
        interactionDataConnector
    )

    val gameSessionId = GameSessionId(123)
    val senderId = PlayerId("sender")
    val receiverId = PlayerId("receiver")

    @Test
    fun `dont accept trade when second player is already in trade`(): Unit = runBlocking {
        coEvery { tradeStatesDataConnector.getPlayerState(gameSessionId, senderId) } returns TradeStates.NoTradeState
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidActive(PlayerId("ktos inny"), TradeBid.empty)

        coEvery {
            (
                interactionProducer.sendMessage(
                    gameSessionId,
                    receiverId,
                    any()
                )
                )
        } returns Unit

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.ProposeTradeUser(senderId, receiverId)
        )

        coVerify(exactly = 1) {
            interactionProducer.sendMessage(
                gameSessionId,
                receiverId,
                any()
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `test error case from bug fixing event xd`(): Unit = runBlocking {
        coEvery { tradeStatesDataConnector.getPlayerState(gameSessionId, senderId) } returns TradeStates.NoTradeState
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidActive(PlayerId("ktos inny"), TradeBid.empty)

        coEvery {
            (
                interactionProducer.sendMessage(
                    gameSessionId,
                    receiverId,
                    any()
                )
                )
        } returns Unit

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.ProposeTradeUser(senderId, receiverId)
        )

        coVerify(atMost = 1) {
            interactionProducer.sendMessage(
                gameSessionId,
                receiverId,
                any()
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }
}
