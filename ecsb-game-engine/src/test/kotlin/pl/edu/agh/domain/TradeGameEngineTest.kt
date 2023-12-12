package pl.edu.agh.domain

import arrow.core.Either
import arrow.core.none
import arrow.core.right
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.trade.domain.AdvertiseDto
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradeInternalMessages.UserInputMessage
import pl.edu.agh.trade.domain.TradeStates
import pl.edu.agh.trade.redis.AdvertisementStateDataConnector
import pl.edu.agh.trade.redis.TradeStatesDataConnector
import pl.edu.agh.trade.service.EquipmentTradeService
import pl.edu.agh.trade.service.TradeGameEngineService
import pl.edu.agh.utils.NonEmptyMap
import java.time.LocalDateTime

class TradeGameEngineTest {
    private val tradeStatesDataConnector = mockk<TradeStatesDataConnector>()
    private val interactionProducer = mockk<InteractionProducer<ChatMessageADT.SystemOutputMessage>>()
    private val interactionDataConnector = mockk<InteractionDataService>()

    private val advertisementDataConnector = object : AdvertisementStateDataConnector {
        override suspend fun getPlayerStates(gameSessionId: GameSessionId): Map<PlayerId, AdvertiseDto> {
            return mapOf()
        }

        override suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): AdvertiseDto {
            return AdvertiseDto(none(), none())
        }

        override suspend fun setPlayerState(
            gameSessionId: GameSessionId,
            playerId: PlayerId,
            newPlayerStatus: AdvertiseDto
        ) = Unit
    }

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
    }

    private val tradeGameEngineService = TradeGameEngineService(
        tradeStatesDataConnector,
        interactionProducer,
        equipmentTradeServiceStub,
        advertisementDataConnector,
        interactionDataConnector
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
        } returns TradeStates.TradeBidActive(receiverId)
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidPassive(senderId)

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
                PlayerIdConst.ECSB_TRADE_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(
                    "Wygląda na to, że wysłałem ofertę do someone else, podczas gdy handluję z receiver",
                    senderId
                )
            )
        }

        coVerify(exactly = 1) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer)
    }

    @Test
    fun `don't propose trade when second player is already in trade`(): Unit = runBlocking {
        coEvery { tradeStatesDataConnector.getPlayerState(gameSessionId, senderId) } returns TradeStates.NoTradeState
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidActive(PlayerId("someone else"))

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
                PlayerIdConst.ECSB_TRADE_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(
                    "receiver handluje obecnie z someone else, musisz poczekać",
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

        coVerify(exactly = 2) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer)
    }

    @Test
    fun `don't start trade when second player is already in trade`(): Unit = runBlocking {
        coEvery { tradeStatesDataConnector.getPlayerState(gameSessionId, senderId) } returns TradeStates.NoTradeState
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidActive(PlayerId("someone else"))

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
                PlayerIdConst.ECSB_TRADE_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(
                    "receiver handluje obecnie z someone else, musisz poczekać",
                    senderId
                )
            )
        }

        coVerify(exactly = 2) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer)
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
                PlayerIdConst.ECSB_TRADE_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage("Cannot start trade with myself", receiverId)
            )
        }

        coVerify(exactly = 1) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer)
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
                PlayerIdConst.ECSB_TRADE_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(
                    "I'm too late, receiver has already proposed someone else",
                    senderId
                )
            )
        }

        coVerify(exactly = 2) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer)
    }

    @Test
    fun `don't finish trade with someone wrong`(): Unit = runBlocking {
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                senderId
            )
        } returns TradeStates.TradeBidActive(receiverId)
        coEvery {
            tradeStatesDataConnector.getPlayerState(
                gameSessionId,
                receiverId
            )
        } returns TradeStates.TradeBidPassive(senderId)

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
                PlayerIdConst.ECSB_TRADE_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(
                    "Wygląda na to, że zaakceptowałem ofertę od someone else, podczas gdy handluję z receiver",
                    senderId
                )
            )
        }

        coVerify(exactly = 1) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 0) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer)
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

        coVerify(exactly = 1) {
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                NonEmptyMap.fromListUnsafe(
                    listOf(
                        senderId to InteractionStatus.TRADE_BUSY,
                        receiverId to InteractionStatus.TRADE_BUSY
                    )
                )
            )
        }

        coVerify(exactly = 4) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 3) { tradeStatesDataConnector.setPlayerState(gameSessionId, any(), any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer, interactionDataConnector)
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
        } returns TradeStates.TradeBidActive(receiverId)

        coEvery {
            tradeStatesDataConnector.getPlayerState(gameSessionId, receiverId)
        } returns TradeStates.TradeBidPassive(senderId)

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
        coVerify(exactly = 2) { tradeStatesDataConnector.getPlayerState(gameSessionId, any()) }
        coVerify(exactly = 2) { interactionDataConnector.removeInteractionData(gameSessionId, any()) }
        confirmVerified(tradeStatesDataConnector, interactionProducer, interactionDataConnector)
    }
}
