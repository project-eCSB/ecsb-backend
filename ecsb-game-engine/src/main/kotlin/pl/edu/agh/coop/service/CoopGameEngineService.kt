package pl.edu.agh.coop.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.redis.CoopStatesDataConnector
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.utils.*
import java.time.LocalDateTime

class CoopGameEngineService(
    private val coopStatesDataConnector: CoopStatesDataConnector,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>,
    private val interactionDataConnector: InteractionDataService = InteractionDataService.instance,
    private val travelCoopService: TravelCoopService = TravelCoopService.instance
) : InteractionConsumer<CoopInternalMessages.UserInputMessage> {
    private val logger by LoggerDelegate()

    private inner class CoopPAMethods(gameSessionId: GameSessionId) {

        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2

        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2

        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2

        val interactionStateSetter = interactionDataConnector::setInteractionData.partially1(gameSessionId)::susTupled2
    }

    override val tSerializer: KSerializer<CoopInternalMessages.UserInputMessage> =
        CoopInternalMessages.UserInputMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "coop-in-$hostTag"
    override fun exchangeName(): String = InteractionProducer.COOP_MESSAGES_EXCHANGE
    override fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), ExchangeType.SHARDING.value)
        channel.queueDeclare(queueName, true, false, true, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: CoopInternalMessages.UserInputMessage
    ) {
        logger.info("Got message from $gameSessionId $senderId sent at $sentAt ($message)")
        when (message) {
            is CoopInternalMessages.UserInputMessage.StartPlanning -> startPlanning(
                gameSessionId,
                senderId,
                message
            )

            CoopInternalMessages.UserInputMessage.FindCompanyForPlanning -> advertiseCoop(gameSessionId, senderId)
            CoopInternalMessages.UserInputMessage.StopFindingCompany -> stopAdvertisingCoop(gameSessionId, senderId)
            is CoopInternalMessages.UserInputMessage.JoinPlanningUser -> guestJoining(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.JoinPlanningAckUser -> acceptGuestJoining(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ProposeCompanyUser -> proposeCoopToPlayer(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser -> acceptCoopWithPlayer(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ResourcesDecideUser -> handleCoopBid(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser -> acceptCoopBid(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> cancelCoop(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> cancelPlanning(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ResourcesGatheredUser -> resourceGathered(
                gameSessionId,
                senderId,
                message.travelerId
            )

            is CoopInternalMessages.UserInputMessage.ResourcesUnGatheredUser -> resourceUnGathered(
                gameSessionId,
                senderId,
                message.secondPlayerId,
                message.equipments
            )
        }.onLeft { logger.error("Can't do this operation now because $it") }
    }

    private suspend fun validateMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        message: CoopInternalMessages
    ): Either<String, Pair<PlayerId, CoopStates>> =
        coopStatesDataConnector
            .getPlayerState(gameSessionId, playerId)
            .let { it.parseCommand(message).map { coopStates -> playerId to coopStates } }

    private suspend fun startPlanning(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.StartPlanning
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, travelName) = message
        val travel = travelCoopService.getTravelByName(gameSessionId, travelName)
            .toEither { "Travel with name $travelName not found in $gameSessionId" }
            .bind()
        val newPlayerStatus = methods.validationMethod(
            senderId to CoopInternalMessages.UserInputMessage.StartPlanning(
                senderId,
                travel.name,
            )
        ).bind()

        methods.playerCoopStateSetter(newPlayerStatus)

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                travelName
            )
        )
    }

    private suspend fun advertiseCoop(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)

        val newPlayerStatus =
            methods.validationMethod(senderId to CoopInternalMessages.UserInputMessage.FindCompanyForPlanning).bind()

        methods.playerCoopStateSetter(newPlayerStatus)

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.AdvertiseCompanySearching(senderId)
        )
    }

    private suspend fun stopAdvertisingCoop(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)

        val newPlayerStatus =
            methods.validationMethod(senderId to CoopInternalMessages.UserInputMessage.StopFindingCompany).bind()

        methods.playerCoopStateSetter(newPlayerStatus)

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.StopCompanySearching(senderId)
        )
    }

    private suspend fun proposeCoopToPlayer(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ProposeCompanyUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, proposalReceiver, travelName) = message
        val newStates = listOf(
            senderId to CoopInternalMessages.UserInputMessage.ProposeCompanyUser(
                senderId,
                proposalReceiver,
                travelName
            ),
            proposalReceiver to CoopInternalMessages.SystemOutputMessage.ProposeCompanySystem(
                senderId,
                proposalReceiver
            )
        ).traverse { methods.validationMethod(it) }
            .flatMap { if (senderId == proposalReceiver) Either.Left("Same receiver") else Either.Right(it) }
            .bind()
            .map { methods.playerCoopStateSetter(it); it }

        newStates.forEach { (player, state) ->
            if (state.busy()) {
                methods.interactionStateSetter(player to InteractionStatus.COOP_BUSY)
            } else {
                methods.interactionStateSetter(player to InteractionStatus.NOT_BUSY)
            }
        }

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.ProposeCompany(proposalReceiver)
        )
    }

    private suspend fun acceptCoopWithPlayer(
        gameSessionId: GameSessionId,
        proposalAccepter: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, proposalSender, travelName) = message
        val playerCoopStates = listOf(
            proposalAccepter to message,
            proposalSender to CoopInternalMessages.SystemOutputMessage.ProposeCompanyAckSystem(
                proposalAccepter,
                proposalSender,
                travelName
            )
        ).traverse { methods.validationMethod(it) }
            .flatMap { if (proposalAccepter == proposalSender) Either.Left("Same receiver") else Either.Right(it) }
            .bind()

        val playerStates = nonEmptyMapOf(
            proposalAccepter to InteractionStatus.COOP_BUSY,
            proposalSender to InteractionStatus.COOP_BUSY
        )
        ensure(
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                playerStates
            )
        ) { logger.error("Player busy already :/"); "Player busy" }
        playerCoopStates.forEach { methods.playerCoopStateSetter(it) }

        listOf(
            proposalSender to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(false, proposalAccepter),
            proposalAccepter to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(true, proposalSender),
            proposalSender to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart(proposalSender),
            proposalAccepter to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart(proposalAccepter)
        ).forEach { methods.interactionSendingMessages(it) }
    }

    private suspend fun guestJoining(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.JoinPlanningUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, proposalReceiver) = message
        val newStates = listOf(
            senderId to message,
            proposalReceiver to CoopInternalMessages.SystemOutputMessage.JoinPlanningSystem(senderId, proposalReceiver)
        ).traverse { methods.validationMethod(it) }
            .flatMap { if (senderId == proposalReceiver) Either.Left("Same receiver") else Either.Right(it) }
            .bind()
            .map { methods.playerCoopStateSetter(it); it }

        newStates.forEach { (player, state) ->
            if (state.busy()) {
                methods.interactionStateSetter(player to InteractionStatus.COOP_BUSY)
            } else {
                methods.interactionStateSetter(player to InteractionStatus.NOT_BUSY)
            }
        }

        listOf(
            senderId to CoopMessages.CoopSystemOutputMessage.JoinPlanning(proposalReceiver)
        ).forEach { methods.interactionSendingMessages(it) }
    }

    private suspend fun acceptGuestJoining(
        gameSessionId: GameSessionId,
        proposalAccepter: PlayerId,
        message: CoopInternalMessages.UserInputMessage.JoinPlanningAckUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, proposalSender) = message
        val proposalAccepterState = methods.validationMethod(
            proposalAccepter to message
        ).flatMap { if (proposalAccepter == proposalSender) Either.Left("Same receiver") else Either.Right(it) }.bind()

        val travel =
            (proposalAccepterState.second as CoopStates.ResourcesDecide.ResourceNegotiatingFirstPassive).travelName
        val proposalSenderState = methods.validationMethod(
            proposalSender to CoopInternalMessages.SystemOutputMessage.JoinPlanningAckSystem(
                proposalAccepter,
                proposalSender,
                travel
            )
        ).bind()

        val playerStates = nonEmptyMapOf(
            proposalAccepter to InteractionStatus.COOP_BUSY,
            proposalSender to InteractionStatus.COOP_BUSY
        )
        ensure(
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                playerStates
            )
        ) { logger.error("Player busy already :/"); "Player busy" }

        methods.playerCoopStateSetter(proposalAccepterState)
        methods.playerCoopStateSetter(proposalSenderState)

        listOf(
            proposalSender to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(false, proposalAccepter),
            proposalAccepter to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(true, proposalSender),
            proposalSender to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart(proposalSender),
            proposalAccepter to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart(proposalAccepter),
        ).forEach { methods.interactionSendingMessages(it) }
    }

    private suspend fun handleCoopBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ResourcesDecideUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, bid, receiverId) = message
        val playerInitalState = coopStatesDataConnector.getPlayerState(gameSessionId, senderId)

        val playerStates = listOf(
            receiverId to CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem(senderId, receiverId),
            senderId to CoopInternalMessages.UserInputMessage.ResourcesDecideUser(senderId, bid, receiverId)
        ).traverse { methods.validationMethod(it) }.bind()

        val travelName = (playerInitalState as CoopStates.ResourcesDecide).travelName()

        val secondPlayerResourceDecideValues = Transactor.dbQuery {
            TravelDao.getTravelByName(gameSessionId, travelName)
                .toEither { "Travel not found ${travelName.value}" }
                .bind()
        }
            .diff(bid)
            .toEither { "Error converting coop negotiation bid" }
            .bind()

        playerStates.forEach { methods.playerCoopStateSetter(it) }

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationBid(
                secondPlayerResourceDecideValues,
                receiverId
            )
        )
    }

    private suspend fun acceptCoopBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, finalBid, receiverId) = message
        val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)

        val newStates = listOf(
            senderId to message,
            receiverId to CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem(
                senderId,
                finalBid,
                receiverId
            )
        ).traverse { methods.validationMethod(it) }.bind()

        logger.info("Finishing coop negotiation for $senderId and $receiverId")
        logger.info("Updating planning states of players $senderId, $receiverId in game session $gameSessionId")

        newStates.forEach { methods.playerCoopStateSetter(it) }

        interactionStateDelete(senderId)
        interactionStateDelete(receiverId)

        listOf(
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop(senderId),
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop(receiverId),
        ).forEach { methods.interactionSendingMessages(it) }

        // EquipmentChangeProducer will send equipments to players (I guess)

        equipmentChangeProducer.sendMessage(gameSessionId, senderId, EquipmentInternalMessage.CheckEquipmentForCoop)
        equipmentChangeProducer.sendMessage(gameSessionId, receiverId, EquipmentInternalMessage.CheckEquipmentForCoop)
    }

//    private suspend fun renegotiateResources(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> =
//        either {
//            val methods = CoopPAMethods(gameSessionId)
//
//            val secondPlayerId = coopStatesDataConnector
//                .getPlayerState(gameSessionId, senderId)
//                .secondPlayer()
//                .toEither { "Second player for coop not found" }
//                .bind()
//
//            val newStates = listOf(
//                senderId to CoopInternalMessages.RenegotiateResourcesRequest,
//                secondPlayerId to CoopInternalMessages.SystemOutputMessage.RenegotiateResourcesRequest
//            ).traverse { methods.validationMethod(it) }.bind()
//
//            val playerStates = nonEmptyMapOf<PlayerId, InteractionStatus>(
//                senderId to InteractionStatus.COOP_BUSY,
//                secondPlayerId to InteractionStatus.COOP_BUSY
//            )
//            ensure(
//                interactionDataConnector.setInteractionDataForPlayers(
//                    gameSessionId,
//                    playerStates
//                )
//            ) { logger.error("Player busy already :/"); "Player busy" }
//            newStates.forEach { methods.playerCoopStateSetter(it) }
//
//            listOf<Pair<PlayerId, CoopMessages.CoopSystemOutputMessage>>(
//                senderId to CoopMessages.CoopSystemOutputMessage.RenegotiateResourcesRequest,
//                secondPlayerId to CoopMessages.CoopSystemOutputMessage.RenegotiateResourcesRequest
//            ).forEach {
//                methods.interactionSendingMessages(it)
//            }
//        }

    private suspend fun resourceUnGathered(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        secondPlayerId: PlayerId,
        equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val senderNewState =
            methods.validationMethod(
                senderId to CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSystem(
                    secondPlayerId,
                    equipments
                )
            ).bind()
        val secondPlayerNewState =
            methods.validationMethod(
                secondPlayerId to CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSystem(
                    senderId,
                    equipments
                )
            ).bind()

        methods.playerCoopStateSetter(senderNewState)
        methods.playerCoopStateSetter(secondPlayerNewState)

        listOf(senderNewState, secondPlayerNewState).forEach { (playerId, _) ->
            methods.interactionSendingMessages(
                playerId to CoopMessages.CoopSystemOutputMessage.ResourceChange(
                    equipments
                )
            )
        }
    }

    private suspend fun resourceGathered(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        secondPlayerId: PlayerId
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val senderNewState =
            methods.validationMethod(
                senderId to CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem(
                    secondPlayerId
                )
            ).bind()
        val secondPlayerNewState =
            methods.validationMethod(
                secondPlayerId to CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem(
                    senderId
                )
            ).bind()

        methods.playerCoopStateSetter(senderNewState)
        methods.playerCoopStateSetter(secondPlayerNewState)

        listOf(senderNewState, secondPlayerNewState).forEach { (playerId, state) ->
            when (state) {
                is CoopStates.GatheringResources -> {
                    val message = if (state.resourcesDecideValues.map { it.playerId }.getOrElse { playerId } == playerId) {
                        CoopMessages.CoopSystemOutputMessage.GoToGateAndTravel(senderId, state.travelName)
                    } else {
                        CoopMessages.CoopSystemOutputMessage.WaitForCoopEnd(
                            secondPlayerId,
                            state.travelName
                        )
                    }
                    methods.interactionSendingMessages(playerId to message)
                }

                else -> logger.error("This state should not be here")
            }
        }
    }

    private suspend fun cancelCoop(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)
        val maybeSecondPlayerId = coopStatesDataConnector.getPlayerState(gameSessionId, senderId).secondPlayer()

        val playerState = methods.validationMethod(senderId to message).bind()

        maybeSecondPlayerId
            .onSome {
                methods.validationMethod(it to CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage).bind()
                methods.playerCoopStateSetter(playerState)
                interactionStateDelete(senderId)
                methods.interactionSendingMessages(
                    senderId to CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage(
                        it
                    )
                )
            }

        methods.playerCoopStateSetter(playerState)
        interactionStateDelete(senderId)
        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage(
                senderId
            )
        )
    }

    private suspend fun cancelPlanning(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage
    ): Either<String, Unit> =
        either {
            val methods = CoopPAMethods(gameSessionId)
            val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)
            val maybeSecondPlayerId = coopStatesDataConnector.getPlayerState(gameSessionId, senderId).secondPlayer()

            val playerState = methods.validationMethod(senderId to message).bind()

            maybeSecondPlayerId
                .onSome {
                    methods.validationMethod(it to CoopInternalMessages.SystemOutputMessage.CancelPlanningAtAnyStage)
                        .bind()
                    methods.playerCoopStateSetter(playerState)
                    interactionStateDelete(senderId)
                    methods.interactionSendingMessages(
                        senderId to CoopMessages.CoopSystemOutputMessage.CancelPlanningAtAnyStage(
                            it
                        )
                    )
                }

            methods.playerCoopStateSetter(playerState)
            interactionStateDelete(senderId)
            methods.interactionSendingMessages(
                senderId to CoopMessages.CoopSystemOutputMessage.CancelPlanningAtAnyStage(
                    senderId
                )
            )
        }
}
