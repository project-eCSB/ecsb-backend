package pl.edu.agh.domain

import arrow.core.*
import io.mockk.*
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
import pl.edu.agh.equipment.domain.EquipmentChangeADT
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.PosInt.Companion.pos
import pl.edu.agh.utils.nonEmptyMapOf
import pl.edu.agh.utils.susTupled2
import java.time.LocalDateTime
import kotlin.test.assertEquals

class CoopGameEngineTest {
    val gameSessionId = GameSessionId(123)
    val senderId = PlayerId("sender")
    val receiverId = PlayerId("receiver")

    val coopInteractionProducerMock = object : InteractionProducer<CoopInternalMessages> {
        override suspend fun sendMessage(
            gameSessionId: GameSessionId,
            senderId: PlayerId,
            message: CoopInternalMessages
        ) {
            coopGameEngineService.callback(gameSessionId, senderId, LocalDateTime.now(), message)
        }
    }
    val coopService = CoopService(coopInteractionProducerMock)

    val coopStatesDataConnector = CoopStatesDataConnectorMock()
    val busyStatusConnectorMock = BusyStatusConnectorMock()

    val interactionProducerStub = mockk<InteractionProducer<ChatMessageADT.SystemInputMessage>>()
    val equipmentChangesProducerStub = mockk<InteractionProducer<EquipmentChangeADT>>()
    val travelCoopServiceStub = object : TravelCoopService {
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
                CoopMessages.CoopSystemInputMessage.ProposeCoop(receiverId)
            )
        } returns Unit

        sendMessage(senderId, CoopMessages.CoopUserInputMessage.ProposeCoop(receiverId))

        coVerify(exactly = 1) {
            interactionProducerStub.sendMessage(
                gameSessionId,
                senderId,
                CoopMessages.CoopSystemInputMessage.ProposeCoop(receiverId)
            )
        }

    }

    @Test
    fun `it should be able to accept coop and have good statuses at the end`(): Unit = runBlocking {
        proposeCoopAndAccept()

        val messagesThatShouldBeSent = listOf(
            senderId to CoopMessages.CoopSystemInputMessage.ProposeCoop(receiverId),
            senderId to ChatMessageADT.SystemInputMessage.NotificationCoopStart(senderId),
            receiverId to ChatMessageADT.SystemInputMessage.NotificationCoopStart(receiverId),
            receiverId to CoopMessages.CoopSystemInputMessage.ProposeCoopAck(senderId)
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

    @Test
    fun `it should perform some magic with cities in city decide and go into resource negotiation`(): Unit =
        runBlocking {
            proposeCoopAndAccept()

            val travelName1 = TravelName("travel1")
            val travelName2 = TravelName("travel2")

            val messages = listOf(
                senderId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 1.pos,
                            travelName2 to 2.pos
                        )
                    )
                ),
                receiverId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 2.pos,
                            travelName2 to 3.pos
                        )
                    )
                ),
                receiverId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 4.pos,
                            travelName2 to 2.pos
                        )
                    )
                ),
                senderId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 8.pos,
                            travelName2 to 1.pos
                        )
                    )
                ),
                senderId to CoopMessages.CoopUserInputMessage.CityDecide(Some(nonEmptyMapOf(travelName1 to 9.pos))),
                senderId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 1.pos,
                            travelName2 to 7.pos
                        )
                    )
                ),
                receiverId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 4.pos,
                            travelName2 to 2.pos
                        )
                    )
                ),

                senderId to CoopMessages.CoopUserInputMessage.CityDecideAck(travelName1),

                receiverId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 2.pos,
                            travelName2 to 3.pos
                        )
                    )
                ),
                receiverId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 4.pos,
                            travelName2 to 2.pos
                        )
                    )
                ),
                senderId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 8.pos,
                            travelName2 to 1.pos
                        )
                    )
                ),
                senderId to CoopMessages.CoopUserInputMessage.CityDecide(Some(nonEmptyMapOf(travelName1 to 9.pos))),
                senderId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 1.pos,
                            travelName2 to 7.pos
                        )
                    )
                ),
                receiverId to CoopMessages.CoopUserInputMessage.CityDecide(
                    Some(
                        nonEmptyMapOf(
                            travelName1 to 4.pos,
                            travelName2 to 2.pos
                        )
                    )
                ),

                receiverId to CoopMessages.CoopUserInputMessage.CityDecideAck(travelName1),
                senderId to CoopMessages.CoopUserInputMessage.CityDecideAck(travelName1)
            )

            messages.forEach { (sendMessage::susTupled2)(it) }

            coVerify(exactly = 2) {
                interactionProducerStub.sendMessage(
                    gameSessionId,
                    senderId,
                    CoopMessages.CoopSystemInputMessage.CityDecideAck(
                        travelName1,
                        receiverId
                    )
                )
            }

            coVerify(exactly = 1) {
                interactionProducerStub.sendMessage(
                    gameSessionId,
                    receiverId,
                    CoopMessages.CoopSystemInputMessage.CityDecideAck(
                        travelName1,
                        senderId
                    )
                )
            }

            val coopStatusesAfterTest = listOf(
                senderId to CoopStates.ResourcesDecide.Passive(receiverId, travelName1, none()),
                receiverId to CoopStates.ResourcesDecide.Active(senderId, travelName1, none())
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


    private fun proposeCoopAndAccept(): Unit = runBlocking {
        coEvery {
            interactionProducerStub.sendMessage(
                gameSessionId,
                any(),
                any()
            )
        } returns Unit

        val messages = listOf(
            senderId to CoopMessages.CoopUserInputMessage.ProposeCoop(receiverId),
            receiverId to CoopMessages.CoopUserInputMessage.ProposeCoopAck(senderId),
        )

        messages.forEach { (sendMessage::susTupled2)(it) }

        val coopStatusesAfterTest = listOf(
            senderId to CoopStates.CityDecide(receiverId, none()),
            receiverId to CoopStates.CityDecide(senderId, none())
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