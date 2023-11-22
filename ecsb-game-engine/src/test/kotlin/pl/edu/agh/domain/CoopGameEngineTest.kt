package pl.edu.agh.domain

import arrow.core.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.coop.service.CoopGameEngineService
import pl.edu.agh.coop.service.CoopService
import pl.edu.agh.coop.service.TravelCoopService
import pl.edu.agh.coop.service.diff
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import java.time.LocalDateTime
import kotlin.test.assertEquals

class CoopGameEngineTest {
    private val gameSessionId = GameSessionId(123)
    private val senderId = PlayerId("sender")
    private val receiverId = PlayerId("receiver")
    private val travelName = TravelName("Warszafka")
    private val secondTravelName = TravelName("Krakówek")
    private val planningStateGathered = nonEmptyMapOf(
        senderId to CoopPlayerEquipment(
            NonEmptyMap.fromListUnsafe(
                listOf(
                    GameResourceName("gówno") to AmountDiff(
                        3.nonNeg,
                        3.nonNeg
                    )
                )
            ),
            none()
        )
    )
    private val coopInteractionProducerMock = object : InteractionProducer<CoopInternalMessages.UserInputMessage> {
        override suspend fun sendMessage(
            gameSessionId: GameSessionId,
            senderId: PlayerId,
            message: CoopInternalMessages.UserInputMessage
        ) {
            coopGameEngineService.callback(gameSessionId, senderId, LocalDateTime.now(), message)
        }
    }
    private val coopService = CoopService(coopInteractionProducerMock)

    private val coopStatesDataConnector = CoopStatesDataConnectorMock()
    private val busyStatusConnectorMock = BusyStatusConnectorMock()

    private val interactionProducerStub = mockk<InteractionProducer<ChatMessageADT.SystemOutputMessage>>()
    private val equipmentChangesProducerStub = mockk<InteractionProducer<EquipmentInternalMessage>>()
    private val travelCoopServiceStub = object : TravelCoopService {
        override suspend fun getTravelCostsByName(
            gameSessionId: GameSessionId,
            travelName: TravelName
        ): Option<NonEmptyMap<GameResourceName, NonNegInt>> =
            nonEmptyMapOf(GameResourceName("gówno") to 6.nonNeg, GameResourceName("gówno2") to 2.nonNeg).some()

        override suspend fun getTravelByName(gameSessionId: GameSessionId, travelName: TravelName): Option<TravelName> =
            travelName.toOption()

        override suspend fun conductPlayerTravel(
            gameSessionId: GameSessionId,
            playerId: PlayerId,
            travelName: TravelName
        ): Either<InteractionException, Unit> {
            TODO("Not yet implemented")
        }

        override suspend fun conductCoopPlayerTravel(
            gameSessionId: GameSessionId,
            travelerId: PlayerId,
            secondId: PlayerId,
            resourcesDecideValues: ResourcesDecideValues,
            travelName: TravelName
        ): Either<InteractionException, Unit> {
            TODO("Not yet implemented")
        }
    }

    private val senderBid = ResourcesDecideValues(
        senderId,
        Percentile(50),
        nonEmptyMapOf(GameResourceName("gówno") to 3.nonNeg, GameResourceName("gówno2") to 0.nonNeg)
    )

    private val receiverBid = runBlocking {
        travelCoopServiceStub.getTravelCostsByName(gameSessionId, travelName)
            .getOrNull()!!
            .diff(senderBid)
            .getOrNull()!!
    }

    val coopGameEngineService = CoopGameEngineService(
        coopStatesDataConnector,
        interactionProducerStub,
        equipmentChangesProducerStub,
        travelCoopServiceStub,
        busyStatusConnectorMock
    )

    val sendMessage = coopService::handleIncomingCoopMessage.partially1(gameSessionId)

    @BeforeEach
    fun reloadMocks() {
        coopStatesDataConnector.resetState()
        busyStatusConnectorMock.resetState()
    }

    @Test
    fun `simple case whether mocks are working`(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                senderId,
                any()
            )
        } returns Unit

        sendMessage(senderId, CoopMessages.CoopUserInputMessage.StartPlanning(travelName))
        sendMessage(senderId, CoopMessages.CoopUserInputMessage.ProposeOwnTravel(travelName, receiverId))

        coVerify(exactly = 1) {
            interactionProducerStub.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(senderId, travelName)
            )
        }

        coVerify(exactly = 1) {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                senderId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }

        coVerify(exactly = 1) {
            interactionProducerStub.sendMessage(
                gameSessionId,
                senderId,
                CoopMessages.CoopSystemOutputMessage.ProposeOwnTravel(receiverId, travelName)
            )
        }
        confirmVerified(equipmentChangesProducerStub, interactionProducerStub)
    }

    @Test
    fun `it should be able to accept negotiation and have good statuses at the end`(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        val messages = listOf(
            senderId to CoopMessages.CoopUserInputMessage.StartPlanning(travelName),
            senderId to CoopMessages.CoopUserInputMessage.ProposeOwnTravel(travelName, receiverId),
            receiverId to CoopMessages.CoopUserInputMessage.ProposeOwnTravelAck(travelName, senderId)
        )

        messages.forEach { (sendMessage::susTupled2)(it) }

        val coopStatusesAfterTest = listOf(
            senderId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstActive(
                senderId,
                receiverId,
                travelName,
                travelName.toOption()
            ),
            receiverId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstPassive(
                receiverId,
                senderId,
                travelName,
                none()
            ),
        )

        coopStatusesAfterTest.forEach { (playerId, status) ->
            assertEquals(status, coopStatesDataConnector.getPlayerState(gameSessionId, playerId))
        }

        val busyStatesAfterTest =
            listOf(senderId to InteractionStatus.COOP_BUSY, receiverId to InteractionStatus.COOP_BUSY)
        busyStatesAfterTest.forEach { (playerId, status) ->
            assertEquals(status.some(), busyStatusConnectorMock.findOne(gameSessionId, playerId))
        }

        val messagesThatShouldBeSent = listOf(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                senderId,
                travelName
            ),
            senderId to CoopMessages.CoopSystemOutputMessage.ProposeOwnTravel(receiverId, travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(receiverId, false, travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(senderId, true, travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart
        )

        messagesThatShouldBeSent.forEach {
            coVerify(exactly = 1) {
                interactionProducerStub.sendMessage(
                    gameSessionId,
                    it.first,
                    it.second
                )
            }
        }

        coVerify(exactly = 1) {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                senderId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }
        confirmVerified(equipmentChangesProducerStub, interactionProducerStub)
    }

    @Test
    fun `it should be able to join someone's planning`(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        val messages = listOf(
            receiverId to CoopMessages.CoopUserInputMessage.StartPlanning(travelName),
            receiverId to CoopMessages.CoopUserInputMessage.AdvertisePlanning(travelName),
            senderId to CoopMessages.CoopUserInputMessage.SimpleJoinPlanning(receiverId),
            receiverId to CoopMessages.CoopUserInputMessage.SimpleJoinPlanningAck(senderId)
        )

        messages.forEach { (sendMessage::susTupled2)(it) }

        val coopStatusesAfterTest = listOf(
            senderId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstActive(
                senderId,
                receiverId,
                travelName,
                none()
            ),
            receiverId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstPassive(
                receiverId,
                senderId,
                travelName,
                travelName.toOption()
            ),
        )

        coopStatusesAfterTest.forEach { (playerId, status) ->
            assertEquals(status, coopStatesDataConnector.getPlayerState(gameSessionId, playerId))
        }

        val busyStatesAfterTest =
            listOf(senderId to InteractionStatus.COOP_BUSY, receiverId to InteractionStatus.COOP_BUSY)
        busyStatesAfterTest.forEach { (playerId, status) ->
            assertEquals(status.some(), busyStatusConnectorMock.findOne(gameSessionId, playerId))
        }

        val messagesThatShouldBeSent = listOf(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                receiverId,
                travelName
            ),
            receiverId to CoopMessages.CoopSystemOutputMessage.StartAdvertisingCoop(travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.SimpleJoinPlanning(receiverId),
            receiverId to CoopMessages.CoopSystemOutputMessage.StopAdvertisingCoop,
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(senderId, true, travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(receiverId, false, travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart
        )

        messagesThatShouldBeSent.forEach {
            coVerify(exactly = 1) {
                interactionProducerStub.sendMessage(
                    gameSessionId,
                    it.first,
                    it.second
                )
            }
        }

        coVerify(exactly = 1) {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                receiverId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }

        confirmVerified(equipmentChangesProducerStub, interactionProducerStub)
    }

    @Test
    fun `it should be able to join someone's planning if also I am planning`(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        val messages = listOf(
            senderId to CoopMessages.CoopUserInputMessage.StartPlanning(secondTravelName),
            receiverId to CoopMessages.CoopUserInputMessage.StartPlanning(travelName),
            receiverId to CoopMessages.CoopUserInputMessage.AdvertisePlanning(travelName),
            senderId to CoopMessages.CoopUserInputMessage.GatheringJoinPlanning(receiverId),
            receiverId to CoopMessages.CoopUserInputMessage.GatheringJoinPlanningAck(senderId),
        )

        messages.forEach { (sendMessage::susTupled2)(it) }

        val coopStatusesAfterTest = listOf(
            receiverId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstPassive(
                receiverId,
                senderId,
                travelName,
                travelName.toOption()
            ),
            senderId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstActive(
                senderId,
                receiverId,
                travelName,
                secondTravelName.toOption()
            ),
        )

        coopStatusesAfterTest.forEach { (playerId, status) ->
            assertEquals(status, coopStatesDataConnector.getPlayerState(gameSessionId, playerId))
        }

        val busyStatesAfterTest =
            listOf(senderId to InteractionStatus.COOP_BUSY, receiverId to InteractionStatus.COOP_BUSY)
        busyStatesAfterTest.forEach { (playerId, status) ->
            assertEquals(status.some(), busyStatusConnectorMock.findOne(gameSessionId, playerId))
        }

        val messagesThatShouldBeSent = listOf(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                senderId,
                secondTravelName
            ),
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                receiverId,
                travelName
            ),
            receiverId to CoopMessages.CoopSystemOutputMessage.StartAdvertisingCoop(travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.GatheringJoinPlanning(receiverId),
            receiverId to CoopMessages.CoopSystemOutputMessage.StopAdvertisingCoop,
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(senderId, true, travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(receiverId, false, travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart
        )

        messagesThatShouldBeSent.forEach {
            coVerify(exactly = 1) {
                interactionProducerStub.sendMessage(
                    gameSessionId,
                    it.first,
                    it.second
                )
            }
        }

        coVerify(exactly = 1) {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                senderId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }

        coVerify(exactly = 1) {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                receiverId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }
        confirmVerified(equipmentChangesProducerStub, interactionProducerStub)
    }

    @Test
    fun `it should be able to join someone's planning and finish coop`(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        val messages = listOf(
            senderId to CoopMessages.CoopUserInputMessage.StartPlanning(travelName),
            senderId to CoopMessages.CoopUserInputMessage.AdvertisePlanning(travelName),
            receiverId to CoopMessages.CoopUserInputMessage.SimpleJoinPlanning(senderId),
            senderId to CoopMessages.CoopUserInputMessage.SimpleJoinPlanningAck(receiverId),
            receiverId to CoopMessages.CoopUserInputMessage.ResourceDecide(receiverBid),
            senderId to CoopMessages.CoopUserInputMessage.ResourceDecideAck(senderBid)
        )

        messages.forEach { (sendMessage::susTupled2)(it) }

        val coopStatusesAfterTest = listOf(
            senderId to CoopStates.GatheringResources(
                senderId,
                travelName,
                (receiverId to senderBid).toOption()
            ),
            receiverId to CoopStates.GatheringResources(
                receiverId,
                travelName,
                (senderId to receiverBid).toOption()
            ),
        )

        coopStatusesAfterTest.forEach { (playerId, status) ->
            assertEquals(status, coopStatesDataConnector.getPlayerState(gameSessionId, playerId))
        }

        assertEquals(none(), busyStatusConnectorMock.findOne(gameSessionId, senderId))
        assertEquals(none(), busyStatusConnectorMock.findOne(gameSessionId, receiverId))

        val messagesThatShouldBeSent = listOf(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                senderId,
                travelName
            ),
            senderId to CoopMessages.CoopSystemOutputMessage.StartAdvertisingCoop(travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.SimpleJoinPlanning(senderId),
            senderId to CoopMessages.CoopSystemOutputMessage.StopAdvertisingCoop,
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(senderId, false, travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(receiverId, true, travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationBid(senderId, senderBid),
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish(
                receiverId
            ),
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish(senderId),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop,
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop,
        )

        messagesThatShouldBeSent.forEach {
            coVerify(exactly = 1) {
                interactionProducerStub.sendMessage(
                    gameSessionId,
                    it.first,
                    it.second
                )
            }
        }

        coVerify(exactly = 2) {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                senderId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }
        confirmVerified(equipmentChangesProducerStub, interactionProducerStub)
    }

    @Test
    fun `it should be able to receive resources gathered when solo cooping`(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coopStatesDataConnector.setPlayerState(
            gameSessionId,
            senderId,
            CoopStates.GatheringResources(senderId, travelName, none())
        )

        coopInteractionProducerMock.sendMessage(
            gameSessionId,
            senderId,
            CoopInternalMessages.UserInputMessage.ResourcesGatheredUser(none(), planningStateGathered)
        )

        coVerify(exactly = 1) {
            interactionProducerStub.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                CoopMessages.CoopSystemOutputMessage.GoToGateAndTravel(senderId, travelName, planningStateGathered)
            )
        }

        val playerState = coopStatesDataConnector.getPlayerState(gameSessionId, senderId)
        assertEquals(playerState, CoopStates.GatheringResources(senderId, travelName, none()))

        confirmVerified(equipmentChangesProducerStub, interactionProducerStub)
    }

    @Test
    fun `test each player has their resources for coop in state after negotiation`(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        coEvery {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        val messages = listOf(
            senderId to CoopMessages.CoopUserInputMessage.StartPlanning(travelName),
            senderId to CoopMessages.CoopUserInputMessage.AdvertisePlanning(travelName),
            receiverId to CoopMessages.CoopUserInputMessage.SimpleJoinPlanning(senderId),
            senderId to CoopMessages.CoopUserInputMessage.SimpleJoinPlanningAck(receiverId),
            receiverId to CoopMessages.CoopUserInputMessage.ResourceDecide(receiverBid),
            senderId to CoopMessages.CoopUserInputMessage.ResourceDecideAck(senderBid)
        )

        messages.forEach { (sendMessage::susTupled2)(it) }

        val coopStatusesAfterTest = listOf(
            receiverId to CoopStates.GatheringResources(
                receiverId,
                travelName,
                (senderId to receiverBid).toOption()
            ),
            senderId to CoopStates.GatheringResources(
                senderId,
                travelName,
                (receiverId to senderBid).toOption()
            ),
        )

        coopStatusesAfterTest.forEach { (playerId, status) ->
            assertEquals(status, coopStatesDataConnector.getPlayerState(gameSessionId, playerId))
        }

        assertEquals(none(), busyStatusConnectorMock.findOne(gameSessionId, senderId))
        assertEquals(none(), busyStatusConnectorMock.findOne(gameSessionId, receiverId))

        val messagesThatShouldBeSent = listOf(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                senderId,
                travelName
            ),
            senderId to CoopMessages.CoopSystemOutputMessage.StartAdvertisingCoop(travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.SimpleJoinPlanning(senderId),
            senderId to CoopMessages.CoopSystemOutputMessage.StopAdvertisingCoop,
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(senderId, false, travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(receiverId, true, travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationBid(senderId, senderBid),
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish(
                receiverId
            ),
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish(senderId),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop,
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop,
        )

        messagesThatShouldBeSent.forEach {
            coVerify(exactly = 1) {
                interactionProducerStub.sendMessage(
                    gameSessionId,
                    it.first,
                    it.second
                )
            }
        }

        coVerify(exactly = 2) {
            equipmentChangesProducerStub.sendMessage(
                gameSessionId,
                senderId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }
        confirmVerified(equipmentChangesProducerStub, interactionProducerStub)
    }
}
