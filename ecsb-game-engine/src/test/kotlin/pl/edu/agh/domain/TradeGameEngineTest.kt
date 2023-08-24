package pl.edu.agh.domain

import arrow.core.Either
import arrow.core.Option
import arrow.core.right
import arrow.core.toOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradeInternalMessages.UserInputMessage
import pl.edu.agh.trade.domain.TradeStates
import pl.edu.agh.trade.redis.TradeStatesDataConnector
import pl.edu.agh.trade.service.EquipmentTradeService
import pl.edu.agh.trade.service.TradeGameEngineService
import java.time.LocalDateTime

class TradeGameEngineTest {
    private val tradeStatesDataConnector = mockk<TradeStatesDataConnector>()
    private val interactionProducer = mockk<InteractionProducer<ChatMessageADT.SystemOutputMessage>>()
    private val interactionDataConnector = mockk<InteractionDataService>()

    private val equipmentTradeServiceStub = object : EquipmentTradeService {
        override suspend fun validateResources(gameSessionId: GameSessionId, tradeBid: TradeBid): Either<String, Unit> {
            return Unit.right()
        }

        override suspend fun finishTrade(
            gameSessionId: GameSessionId,
            finalBid: TradeBid,
            senderId: PlayerId,
            receiverId: PlayerId
        ): Either<String, Unit> {
            return Unit.right()
        }

        override suspend fun getPlayersEquipmentsForTrade(
            gameSessionId: GameSessionId,
            player1: PlayerId,
            player2: PlayerId
        ): Option<Pair<PlayerEquipment, PlayerEquipment>> {
            return (PlayerEquipment.empty to PlayerEquipment.empty).toOption()
        }
    }

    private val tradeGameEngineService = TradeGameEngineService(
        tradeStatesDataConnector,
        interactionProducer,
        interactionDataConnector,
        equipmentTradeServiceStub
    )

    private val gameSessionId = GameSessionId(123)
    private val senderId = PlayerId("sender")
    private val receiverId = PlayerId("receiver")

    @Test
    fun `don't sent trade bid with someone wrong`(): Unit = runBlocking {
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                senderId
            )
        } returns TradeStates.TradeBidActive(receiverId, TradeBid.empty)
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidPassive(senderId, TradeBid.empty)

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.TradeBidUser(TradeBid.empty, PlayerId("someone else"))
        )

        coVerify(exactly = 1) {
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserBusyMessage(
                    "Looks like I sent bid to someone else, it should have been receiver",
                    senderId
                )
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `don't propose trade when second player is already in trade`(): Unit = runBlocking {
        coEvery { tradeStatesDataConnector.getPlayerState(gameSessionId, senderId) } returns TradeStates.NoTradeState
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidActive(PlayerId("someone else"), TradeBid.empty)

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
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
                PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserBusyMessage(
                    "receiver is in trade with someone else, leave him alone",
                    senderId
                )
            )
        }

        coVerify(exactly = 0) {
            interactionProducer.sendMessage(
                gameSessionId,
                senderId,
                any()
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `don't start trade when second player is already in trade`(): Unit = runBlocking {
        coEvery { tradeStatesDataConnector.getPlayerState(gameSessionId, senderId) } returns TradeStates.NoTradeState
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidActive(PlayerId("someone else"), TradeBid.empty)

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.ProposeTradeAckUser(receiverId)
        )

        coVerify(exactly = 1) {
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserBusyMessage(
                    "receiver is in trade with someone else, leave him alone",
                    senderId
                )
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `don't start trade with myself after proposing someone else`(): Unit = runBlocking {
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.WaitingForLastProposal(receiverId, PlayerId("someone else"))

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        tradeGameEngineService.callback(
            gameSessionId,
            receiverId,
            LocalDateTime.now(),
            UserInputMessage.ProposeTradeAckUser(receiverId)
        )

        coVerify(exactly = 1) {
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserBusyMessage("Cannot start trade with myself", receiverId)
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `don't start trade with guy who already send propose to someone else`(): Unit = runBlocking {
        coEvery { tradeStatesDataConnector.getPlayerState(gameSessionId, senderId) } returns TradeStates.NoTradeState
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.WaitingForLastProposal(receiverId, PlayerId("someone else"))

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.ProposeTradeAckUser(receiverId)
        )

        coVerify(exactly = 1) {
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserBusyMessage(
                    "I'm too late, receiver has already proposed someone else",
                    senderId
                )
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `don't finish trade with someone wrong`(): Unit = runBlocking {
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                senderId
            )
        } returns TradeStates.TradeBidActive(receiverId, TradeBid.empty)
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidPassive(senderId, TradeBid.empty)

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.TradeBidAckUser(TradeBid.empty, PlayerId("someone else"))
        )

        coVerify(exactly = 1) {
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserBusyMessage(
                    "Looks like I accepted bid to someone else, it should have been receiver",
                    senderId
                )
            )
        }

        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `start normal trade`(): Unit = runBlocking {
        coEvery {
            tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any())
        } returns Unit

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                any()
            )
        } returns true

        coEvery {
            tradeStatesDataConnector.getPlayerState(gameSessionId, any())
        } returns TradeStates.NoTradeState

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.ProposeTradeUser(senderId, receiverId)
        )

        coEvery {
            tradeStatesDataConnector.getPlayerState(gameSessionId, senderId)
        } returns TradeStates.WaitingForLastProposal(senderId, receiverId)

        tradeGameEngineService.callback(
            gameSessionId,
            receiverId,
            LocalDateTime.now(),
            UserInputMessage.ProposeTradeAckUser(senderId)
        )

        coVerify(exactly = 5) {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        }

        coVerify(exactly = 3) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
    }

    @Test
    fun `finish normal trade`(): Unit = runBlocking {
        coEvery {
            tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any())
        } returns Unit

        coEvery {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            interactionDataConnector.removeInteractionData(
                gameSessionId,
                any()
            )
        } returns Unit

        coEvery {
            tradeStatesDataConnector.getPlayerState(gameSessionId, senderId)
        } returns TradeStates.TradeBidActive(receiverId, TradeBid.empty)

        coEvery {
            tradeStatesDataConnector.getPlayerState(gameSessionId, receiverId)
        } returns TradeStates.TradeBidPassive(senderId, TradeBid.empty)

        tradeGameEngineService.callback(
            gameSessionId,
            senderId,
            LocalDateTime.now(),
            UserInputMessage.TradeBidAckUser(TradeBid.empty, receiverId)
        )

        coVerify(exactly = 4) {
            interactionProducer.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        }

        coVerify(exactly = 2) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        coVerify(exactly = 2) { interactionDataConnector.removeInteractionData(gameSessionId, any()) }
    }
}
