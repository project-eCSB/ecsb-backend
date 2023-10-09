package pl.edu.agh.domain

import arrow.core.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.service.CoopGameEngineService
import pl.edu.agh.coop.service.CoopService
import pl.edu.agh.coop.service.TravelCoopService
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.PosInt.Companion.pos
import pl.edu.agh.utils.susTupled2
import java.time.LocalDateTime
import kotlin.test.assertEquals

class CoopGameEngineTest {
    private val gameSessionId = GameSessionId(123)
    private val senderId = PlayerId("sender")
    private val receiverId = PlayerId("receiver")
    private val travelName = TravelName("Warszafka")

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
        override suspend fun getTravelByName(
            gameSessionId: GameSessionId,
            travelName: TravelName
        ): Option<GameTravelsView> = GameTravelsView(
            travelName,
            none(),
            Range<PosInt>(1.pos, 2.pos),
            PlayerEquipment.empty.resources
        ).some()

        override suspend fun getTravelByNames(
            gameSessionId: GameSessionId,
            names: NonEmptySet<TravelName>
        ): Option<NonEmptySet<GameTravelsView>> =
            names.map {
                GameTravelsView(
                    it,
                    none(),
                    Range<PosInt>(1.pos, 2.pos),
                    PlayerEquipment.empty.resources
                )
            }.some()
    }

    val coopGameEngineService = CoopGameEngineService(
        coopStatesDataConnector,
        interactionProducerStub,
        equipmentChangesProducerStub,
        busyStatusConnectorMock,
        travelCoopServiceStub
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
                senderId,
                any()
            )
        } returns Unit

        sendMessage(senderId, CoopMessages.CoopUserInputMessage.StartPlanning(travelName))
        sendMessage(senderId, CoopMessages.CoopUserInputMessage.ProposeCompany(travelName, receiverId))

        coVerify(exactly = 1) {
            interactionProducerStub.sendMessage(
                gameSessionId,
                senderId,
                CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(travelName)
            )
        }

        coVerify(exactly = 1) {
            interactionProducerStub.sendMessage(
                gameSessionId,
                senderId,
                CoopMessages.CoopSystemOutputMessage.ProposeCompany(receiverId)
            )
        }
    }

    @Test
    fun `it should be able to accept coop and have good statuses at the end`(): Unit = runBlocking {
        proposeCoopAndAccept()

        val messagesThatShouldBeSent = listOf(
            senderId to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.ProposeCompany(receiverId),
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(false, receiverId),
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(true, senderId),
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart(senderId),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart(receiverId)
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
    }

    private fun proposeCoopAndAccept(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        val messages = listOf(
            senderId to CoopMessages.CoopUserInputMessage.StartPlanning(travelName),
            senderId to CoopMessages.CoopUserInputMessage.ProposeCompany(travelName, receiverId),
            receiverId to CoopMessages.CoopUserInputMessage.ProposeCompanyAck(travelName, senderId)
        )

        messages.forEach { (sendMessage::susTupled2)(it) }

        val coopStatusesAfterTest = listOf(
            senderId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstActive(
                senderId,
                receiverId,
                travelName,
                true
            ),
            receiverId to CoopStates.ResourcesDecide.ResourceNegotiatingFirstPassive(
                receiverId,
                senderId,
                travelName,
                false
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
    }
}
