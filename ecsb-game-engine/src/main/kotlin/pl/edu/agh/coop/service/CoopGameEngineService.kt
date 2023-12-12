package pl.edu.agh.coop.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.domain.TravelSet
import pl.edu.agh.coop.redis.CoopStatesDataConnector
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.*
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

class CoopGameEngineService(
    private val coopStatesDataConnector: CoopStatesDataConnector,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>,
    private val travelCoopService: TravelCoopService,
    private val interactionDataConnector: InteractionDataService = InteractionDataService.instance
) : InteractionConsumer<CoopInternalMessages.UserInputMessage> {
    private val logger by LoggerDelegate()
    private val timeout = 5.seconds

    private inner class CoopPAMethods(gameSessionId: GameSessionId) {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val playerCoopStateGetter = coopStatesDataConnector::getPlayerState.partially1(gameSessionId)
    }

    override val tSerializer: KSerializer<CoopInternalMessages.UserInputMessage> =
        CoopInternalMessages.UserInputMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "coop-in-$hostTag"
    override fun exchangeName(): String = InteractionProducer.COOP_MESSAGES_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.SHARDING
    override fun autoDelete(): Boolean = true

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

            is CoopInternalMessages.UserInputMessage.StartAdvertisingCoop -> advertiseCoop(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.StopAdvertisingCoop -> stopAdvertisingCoop(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.SimpleJoinPlanningUser -> joinPlanning(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.SimpleJoinPlanningAckUser -> acceptJoiningToYou(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.GatheringJoinPlanningUser -> joinPlanning(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.GatheringJoinPlanningAckUser -> acceptJoiningToYou(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelUser -> proposeOwnTravel(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelAckUser -> acceptOwnerProposal(
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

            is CoopInternalMessages.UserInputMessage.CancelNegotiationAtAnyStage -> cancelNegotiation(
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
                message.travelerId,
                message.equipments
            )

            is CoopInternalMessages.UserInputMessage.ResourcesUnGatheredUser -> resourceUnGathered(
                gameSessionId,
                senderId,
                message.secondPlayerId,
                message.equipments
            )

            is CoopInternalMessages.UserInputMessage.ResourcesUnGatheredSingleUser -> resourceUnGatheredSingle(
                gameSessionId,
                senderId,
                message.equipment
            )

            is CoopInternalMessages.UserInputMessage.StartPlannedTravel -> conductTravel(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.StartSimpleTravel -> conductSimpleTravel(
                gameSessionId,
                senderId,
                message
            )

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> handleSessionExit(
                gameSessionId,
                senderId,
                message
            )

            CoopInternalMessages.UserInputMessage.SyncAdvertisement -> handleSyncAdvertisement(
                gameSessionId,
                senderId
            )
        }.onLeft {
            logger.warn("WARNING: $it, GAME: ${GameSessionId.toName(gameSessionId)}, SENDER: ${senderId.value}, SENT AT: $sentAt, SOURCE: $message")
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(it, senderId)
            )
        }
    }

    private suspend fun validateMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        message: CoopInternalMessages
    ): Either<String, Pair<PlayerId, CoopStates>> =
        coopStatesDataConnector
            .getPlayerState(gameSessionId, playerId)
            .let { it.parseCommand(message).map { coopStates -> playerId to coopStates } }

    private suspend fun handleSyncAdvertisement(
        gameSessionId: GameSessionId,
        senderId: PlayerId
    ): Either<String, Unit> = either {
        val states = coopStatesDataConnector.getPlayerStates(gameSessionId).filterNot { (key, _) -> key == senderId }
            .mapValues { (_, value) -> if (value is CoopStates.WaitingForCompany && value.isAdvertising) value.travelName.some() else none() }
            .filterOption()

        interactionProducer.sendMessage(
            gameSessionId,
            PlayerIdConst.ECSB_CHAT_PLAYER_ID,
            CoopMessages.CoopSystemOutputMessage.AdvertisingSync(senderId, states.toNonEmptyMapOrNone())
        )
    }

    private suspend fun startPlanning(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.StartPlanning
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, travelName) = message
        val travel = travelCoopService.getTravelByName(gameSessionId, travelName)
            .toEither { "Podróż o nazwie $travelName nie została znaleziona w sesji $gameSessionId" }
            .bind()
        val oldPlayerState = methods.playerCoopStateGetter(senderId)
        val newPlayerState = methods.validationMethod(
            senderId to CoopInternalMessages.UserInputMessage.StartPlanning(
                senderId,
                travel,
            )
        ).bind()

        methods.playerCoopStateSetter(newPlayerState)

        stopAdvertisingNotification(oldPlayerState, gameSessionId)

        methods.interactionSendingMessages(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.StartPlanningSystem(
                senderId,
                travelName
            )
        )
        equipmentChangeProducer.sendMessage(
            gameSessionId,
            senderId,
            EquipmentInternalMessage.CheckEquipmentsForCoop
        )
    }

    private suspend fun advertiseCoop(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.StartAdvertisingCoop
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val newPlayerState = methods.validationMethod(senderId to message).bind()
        val travelName = newPlayerState.second.travelName()
            .toEither { "Nazwa wyprawy jest wymagana w przypadku ogłaszania współpracy" }.bind()

        methods.playerCoopStateSetter(newPlayerState)
        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.StartAdvertisingCoop(
                travelName
            )
        )
    }

    private suspend fun stopAdvertisingCoop(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.StopAdvertisingCoop
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        methods.playerCoopStateSetter(methods.validationMethod(senderId to message).bind())
        methods.interactionSendingMessages(senderId to CoopMessages.CoopSystemOutputMessage.StopAdvertisingCoop)
    }

    private suspend fun proposeOwnTravel(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ProposeOwnTravelUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, proposalReceiver, travelName) = message
        listOf(
            senderId to message,
            proposalReceiver to CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelSystem(
                senderId,
                proposalReceiver
            )
        ).traverse { methods.validationMethod(it) }
            .flatMap {
                if (senderId == proposalReceiver) {
                    Either.Left("Ten sam odbiorca co nadawca we wiadomości $message")
                } else {
                    Either.Right(it)
                }
            }
            .bind()
            .map { methods.playerCoopStateSetter(it); }

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.ProposeOwnTravel(proposalReceiver, travelName)
        )
    }

    private suspend fun acceptOwnerProposal(
        gameSessionId: GameSessionId,
        proposalReceiver: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ProposeOwnTravelAckUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val (_, proposalSender, travelName) = message
        val newPlayerCoopStates = listOf(
            proposalReceiver to message,
            proposalSender to CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelAckSystem(
                proposalReceiver,
                proposalSender,
                travelName
            )
        ).traverse { methods.validationMethod(it) }
            .flatMap {
                if (proposalReceiver == proposalSender) {
                    Either.Left("Ten sam odbiorca co nadawca we wiadomości $message")
                } else {
                    Either.Right(it)
                }
            }
            .bind()

        startCoopNegotiation(
            methods,
            gameSessionId,
            proposalReceiver,
            proposalSender,
            newPlayerCoopStates,
            travelName
        ).bind()
    }

    private suspend fun joinPlanning(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val joiningReceiver: PlayerId
        val stateMessage: CoopInternalMessages.SystemOutputMessage
        val outMessage: CoopMessages.CoopSystemOutputMessage
        when (message) {
            is CoopInternalMessages.UserInputMessage.SimpleJoinPlanningUser -> {
                joiningReceiver = message.joiningReceiver
                stateMessage =
                    CoopInternalMessages.SystemOutputMessage.SimpleJoinPlanningSystem(senderId, joiningReceiver)
                outMessage = CoopMessages.CoopSystemOutputMessage.SimpleJoinPlanning(joiningReceiver)
            }

            is CoopInternalMessages.UserInputMessage.GatheringJoinPlanningUser -> {
                joiningReceiver = message.joiningReceiver
                stateMessage =
                    CoopInternalMessages.SystemOutputMessage.GatheringJoinPlanningSystem(senderId, joiningReceiver)
                outMessage = CoopMessages.CoopSystemOutputMessage.GatheringJoinPlanning(joiningReceiver)
            }

            else -> {
                raise("Błędna wiadomość przekazana do funkcji dołączania do współpracy: $message")
            }
        }
        listOf(
            senderId to message,
            joiningReceiver to stateMessage
        ).traverse { methods.validationMethod(it) }
            .flatMap {
                if (senderId == joiningReceiver) {
                    Either.Left("Ten sam odbiorca co nadawca we wiadomości $message")
                } else {
                    Either.Right(it)
                }
            }
            .bind()
            .map { methods.playerCoopStateSetter(it) }

        methods.interactionSendingMessages(senderId to outMessage)
    }

    private suspend fun acceptJoiningToYou(
        gameSessionId: GameSessionId,
        joiningReceiverId: PlayerId,
        message: CoopInternalMessages.UserInputMessage
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val joiningSenderId: PlayerId
        val stateMessage: CoopInternalMessages.SystemOutputMessage
        val newJoiningReceiverState = methods.validationMethod(joiningReceiverId to message).bind()
        val travelName = newJoiningReceiverState.second.travelName()
            .toEither { "Ogłaszając się musisz wykazać nazwę wyprawy" }.bind()
        when (message) {
            is CoopInternalMessages.UserInputMessage.SimpleJoinPlanningAckUser -> {
                joiningSenderId = message.joiningSender
                stateMessage =
                    CoopInternalMessages.SystemOutputMessage.SimpleJoinPlanningAckSystem(
                        joiningReceiverId,
                        joiningSenderId,
                        travelName
                    )
            }

            is CoopInternalMessages.UserInputMessage.GatheringJoinPlanningAckUser -> {
                joiningSenderId = message.joiningSender
                stateMessage =
                    CoopInternalMessages.SystemOutputMessage.GatheringJoinPlanningAckSystem(
                        joiningReceiverId,
                        joiningSenderId,
                        travelName
                    )
            }

            else -> {
                raise("Błędna wiadomość przekazana do funkcji akceptacji dołączania do współpracy: $message")
            }
        }

        ensure(joiningSenderId != joiningReceiverId) {
            logger.error("Same receiver in message $message"); "Ten sam odbiorca co nadawca we wiadomości $message"
        }

        val newJoiningSenderState = methods.validationMethod(joiningSenderId to stateMessage).bind()

        startCoopNegotiation(
            methods,
            gameSessionId,
            joiningReceiverId,
            joiningSenderId,
            listOf(newJoiningReceiverState, newJoiningSenderState),
            travelName
        ).bind()
    }

    private suspend fun startCoopNegotiation(
        methods: CoopPAMethods,
        gameSessionId: GameSessionId,
        receiverId: PlayerId,
        senderId: PlayerId,
        newPlayerCoopStates: List<Pair<PlayerId, CoopStates>>,
        travelName: TravelName
    ): Either<String, Unit> = either {
        ensure(
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                nonEmptyMapOf(
                    receiverId to InteractionStatus.COOP_BUSY,
                    senderId to InteractionStatus.COOP_BUSY
                )
            )
        ) {
            logger.error("$receiverId or $senderId is busy, so they could not start negotiation")
            "${receiverId.value} lub ${senderId.value} jest zajęty, więc nie mogą rozpocząć negocjacji"
        }

        val oldReceiverState = methods.playerCoopStateGetter(receiverId)
        val oldSenderState = methods.playerCoopStateGetter(senderId)
        newPlayerCoopStates.forEach { methods.playerCoopStateSetter(it) }

        listOf(oldReceiverState, oldSenderState).forEach { stopAdvertisingNotification(it, gameSessionId) }

        listOf(
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(receiverId, false, travelName),
            receiverId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart(senderId, true, travelName),
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart,
            receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStart
        ).forEach {
            methods.interactionSendingMessages(it)
        }
    }

    private suspend fun handleCoopBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ResourcesDecideUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val oldPlayerState = methods.playerCoopStateGetter(senderId)
        val receiverId =
            oldPlayerState.secondPlayer().toEither { "Drugi gracz musi być sprecyzowany na etapie negocjacji" }.bind()

        val travelName =
            oldPlayerState.travelName().toEither { "Nazwa wyprawy musi być sprecyzowana na etapie negocjacji" }.bind()

        val secondPlayerResourceDecideValues = travelCoopService.getTravelCostsByName(gameSessionId, travelName)
            .toEither { "Podróż nieznaleziona: ${travelName.value}" }
            .bind()
            .diff(message.bid)
            .toEither { "Błąd przy przekształcaniu oferty współpracy" }
            .bind()

        listOf(
            senderId to message,
            receiverId to CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem(
                secondPlayerResourceDecideValues
            )
        ).traverse { methods.validationMethod(it) }.bind()
            .map { methods.playerCoopStateSetter(it) }

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationBid(
                receiverId,
                secondPlayerResourceDecideValues
            )
        )
    }

    private suspend fun acceptCoopBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val finalSenderBid = message.bid

        val receiverId = methods.playerCoopStateGetter(senderId).secondPlayer()
            .toEither { "Drugi gracz musi być sprecyzowany na etapie negocjacji" }.bind()
        val oldReceiverState = methods.playerCoopStateGetter(receiverId)

        if (oldReceiverState is CoopStates.ResourcesDecide.ResourceNegotiatingPassive) {
            val convertedReceiverBid =
                travelCoopService.getTravelCostsByName(gameSessionId, oldReceiverState.travelName)
                    .toEither { "Podróż nieznaleziona: ${oldReceiverState.travelName.value}" }
                    .bind()
                    .diff(oldReceiverState.sentBid)
                    .toEither { "Błąd przy przekształcaniu oferty współpracy" }
                    .bind()

            ensure(finalSenderBid == convertedReceiverBid) { "Zaakceptowana oferta to nie ta, którą $receiverId wysłał do $senderId, $finalSenderBid, $convertedReceiverBid" }

            val newStates = listOf(
                senderId to message,
                receiverId to CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem(
                    senderId,
                    oldReceiverState.sentBid,
                    receiverId
                )
            ).traverse { methods.validationMethod(it) }.bind()

            logger.info("Finishing coop negotiation for $senderId and $receiverId")
            logger.info("Updating planning states of players $senderId, $receiverId in game session $gameSessionId")

            newStates.forEach { methods.playerCoopStateSetter(it) }

            listOf(senderId, receiverId).forEach {
                interactionDataConnector.removeInteractionData(gameSessionId, it)
            }

            listOf(
                PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish(
                    senderId
                ),
                PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish(
                    receiverId
                ),
                senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop,
                receiverId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop
            ).forEach { methods.interactionSendingMessages(it) }

            equipmentChangeProducer.sendMessage(
                gameSessionId,
                senderId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        } else {
            raise("Zły stan podczas akcpeptonia oferty współpracy: $oldReceiverState")
        }
    }

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

        val travelName =
            senderNewState.second.travelName()
                .toEither { "Nazwa wyprawy musi być sprecyzowana przy wspólnym zbieraniu zasobów" }.bind()

        listOf(senderNewState, secondPlayerNewState).forEach { methods.playerCoopStateSetter(it) }

        methods.interactionSendingMessages(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.CoopResourceChange(
                travelName,
                equipments
            )
        )
    }

    private suspend fun resourceUnGatheredSingle(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        equipment: CoopPlayerEquipment
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val senderNewState =
            methods.validationMethod(
                senderId to CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSingleSystem(equipment)
            ).bind()
        val travelName = senderNewState.second.travelName()
            .toEither { "Nazwa wyprawy musi być sprecyzowana przy wspólnym zbieraniu zasobów" }.bind()

        methods.playerCoopStateSetter(senderNewState)
        methods.interactionSendingMessages(
            PlayerIdConst.ECSB_COOP_PLAYER_ID to CoopMessages.CoopSystemOutputMessage.CoopResourceChange(
                travelName,
                nonEmptyMapOf(senderId to equipment)
            )
        )
    }

    private suspend fun resourceGathered(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        maybeSecondPlayerId: Option<PlayerId>,
        equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val senderNewState =
            methods.validationMethod(
                senderId to CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem
            ).bind()
        val secondPlayerNewState = maybeSecondPlayerId.map {
            methods.validationMethod(
                it to CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem
            ).bind()
        }

        val states = listOf(senderNewState.some(), secondPlayerNewState).filterOption()
        states.forEach { methods.playerCoopStateSetter(it) }

        states.forEach { (playerId, state) ->
            if (state is TravelSet) {
                state.travelName().onSome { travelName ->
                    val message =
                        if (state.traveller() == playerId) {
                            CoopMessages.CoopSystemOutputMessage.GoToGateAndTravel(playerId, travelName, equipments)
                        } else {
                            CoopMessages.CoopSystemOutputMessage.WaitForCoopEnd(
                                playerId,
                                state.traveller(),
                                travelName,
                                equipments
                            )
                        }
                    methods.interactionSendingMessages(PlayerIdConst.ECSB_COOP_PLAYER_ID to message)
                }.onNone {
                    logger.error("Travel name needed if gathering resources state is used")
                }
            } else {
                logger.error("This state should not appear if gathering resources")
            }
        }
    }

    private suspend fun conductTravel(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.StartPlannedTravel
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val oldSenderState = methods.playerCoopStateGetter(senderId)
        val newSenderState = methods.validationMethod(senderId to message).bind()
        if (oldSenderState.secondPlayer().isSome() && oldSenderState is CoopStates.GatheringResources) {
            val (_, travelName, negotiatedBid) = oldSenderState
            val (secondPlayer, resourcesBid) = negotiatedBid.toEither { "Błąd przy pobieraniu wynegocjowanego podziału" }
                .bind()
            val oldSecondPlayerState = methods.playerCoopStateGetter(secondPlayer)
            ensure(oldSecondPlayerState is CoopStates.GatheringResources) { "Błędny stan drugiego gracza przy odbywaniu podróży: $oldSecondPlayerState" }
            val newSecondPlayerNewState = methods.validationMethod(
                secondPlayer to CoopInternalMessages.SystemOutputMessage.StartPlannedTravel(travelName)
            ).bind()
            travelCoopService.conductCoopPlayerTravel(gameSessionId, senderId, secondPlayer, resourcesBid, travelName)
                .onLeft {
                    interactionProducer.sendMessage(
                        gameSessionId,
                        PlayerIdConst.ECSB_COOP_PLAYER_ID,
                        CoopMessages.CoopSystemOutputMessage.TravelDeny(senderId, it.toResponsePairLogging().second)
                    )
                }.onRight {
                    interactionProducer.sendMessage(
                        gameSessionId,
                        PlayerIdConst.ECSB_COOP_PLAYER_ID,
                        CoopMessages.CoopSystemOutputMessage.TravelAccept(
                            senderId,
                            TimestampMillis(timeout.inWholeMilliseconds)
                        )
                    )
                    interactionProducer.sendMessage(
                        gameSessionId,
                        PlayerIdConst.ECSB_COOP_PLAYER_ID,
                        CoopMessages.CoopSystemOutputMessage.CoopFinish(secondPlayer, senderId, travelName)
                    )
                    methods.playerCoopStateSetter(newSenderState)
                    methods.playerCoopStateSetter(newSecondPlayerNewState)
                }
        } else {
            invokeTravelService(
                gameSessionId,
                senderId,
                message.travelName,
                oldSenderState,
                newSenderState.second
            )
        }.mapLeft { it.toResponsePairLogging().second }.bind()
    }

    private suspend fun conductSimpleTravel(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.StartSimpleTravel
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val newSenderState = methods.validationMethod(senderId to message).bind()
        invokeTravelService(
            gameSessionId,
            senderId,
            message.travelName,
            methods.playerCoopStateGetter(senderId),
            newSenderState.second
        ).mapLeft { it.toResponsePairLogging().second }.bind()
    }

    private suspend fun invokeTravelService(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        travelName: TravelName,
        oldSenderState: CoopStates,
        newSenderState: CoopStates
    ): Either<InteractionException, Unit> =
        travelCoopService.conductPlayerTravel(gameSessionId, senderId, travelName)
            .onLeft {
                interactionProducer.sendMessage(
                    gameSessionId,
                    PlayerIdConst.ECSB_COOP_PLAYER_ID,
                    CoopMessages.CoopSystemOutputMessage.TravelDeny(senderId, it.toResponsePairLogging().second)
                )
            }.onRight {
                interactionProducer.sendMessage(
                    gameSessionId,
                    PlayerIdConst.ECSB_COOP_PLAYER_ID,
                    CoopMessages.CoopSystemOutputMessage.TravelAccept(
                        senderId,
                        TimestampMillis(timeout.inWholeMilliseconds)
                    )
                )
                stopAdvertisingNotification(oldSenderState, gameSessionId)
                coopStatesDataConnector.setPlayerState(gameSessionId, senderId, newSenderState)
            }

    private suspend fun cancelNegotiation(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.CancelNegotiationAtAnyStage
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val oldSenderState = methods.playerCoopStateGetter(senderId)
        val secondPlayerId =
            oldSenderState.secondPlayer().toEither { "Aby anulować negocjacje, musisz być w negocjacjach" }.bind()

        val newStates = listOf(
            senderId to message,
            secondPlayerId to CoopInternalMessages.SystemOutputMessage.CancelNegotiationAtAnyStage
        ).traverse { methods.validationMethod(it) }.bind()

        newStates.forEach { methods.playerCoopStateSetter(it) }

        listOf(senderId, secondPlayerId).forEach {
            interactionDataConnector.removeInteractionData(gameSessionId, it)
        }

        listOf(
            senderId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop,
            secondPlayerId to CoopMessages.CoopSystemOutputMessage.NotificationCoopStop,
            senderId to CoopMessages.CoopSystemOutputMessage.CancelNegotiationAtAnyStage(senderId),
            senderId to CoopMessages.CoopSystemOutputMessage.CancelNegotiationAtAnyStage(secondPlayerId)
        ).forEach { methods.interactionSendingMessages(it) }

        listOf(senderId, secondPlayerId).forEach {
            equipmentChangeProducer.sendMessage(
                gameSessionId,
                it,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }
    }

    private suspend fun cancelCoop(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val oldSenderState = methods.playerCoopStateGetter(senderId)
        cancelCoopForSender(methods, oldSenderState, gameSessionId, senderId, message)
        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage(
                senderId
            )
        )
        equipmentChangeProducer.sendMessage(
            gameSessionId,
            senderId,
            EquipmentInternalMessage.CheckEquipmentsForCoop
        )
        oldSenderState.secondPlayer().onSome {
            cancelCoopForSecondPlayer(
                methods,
                gameSessionId,
                senderId,
                it,
                CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage,
                CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage(it)
            )
        }
    }

    private suspend fun cancelPlanning(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val oldSenderState = methods.playerCoopStateGetter(senderId)
        cancelCoopForSender(methods, oldSenderState, gameSessionId, senderId, message).bind()
        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.CancelPlanningAtAnyStage(
                senderId
            )
        )
    }

    private suspend fun handleSessionExit(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: CoopInternalMessages.UserInputMessage.ExitGameSession
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val oldSenderState = methods.playerCoopStateGetter(senderId)
        cancelCoopForSender(methods, oldSenderState, gameSessionId, senderId, message)

        oldSenderState.secondPlayer().onSome {
            val internalMessage: CoopInternalMessages.SystemOutputMessage
            val outputMessage: CoopMessages.CoopSystemOutputMessage
            if (oldSenderState is CoopStates.ResourcesDecide) {
                internalMessage = CoopInternalMessages.SystemOutputMessage.CancelNegotiationAtAnyStage
                outputMessage = CoopMessages.CoopSystemOutputMessage.CancelNegotiationAtAnyStage(it)
            } else {
                internalMessage = CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage
                outputMessage = CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage(it)
            }
            cancelCoopForSecondPlayer(
                methods,
                gameSessionId,
                senderId,
                it,
                internalMessage,
                outputMessage
            )
        }
    }

    private suspend fun cancelCoopForSender(
        methods: CoopPAMethods,
        oldSenderState: CoopStates,
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        internalMessage: CoopInternalMessages.UserInputMessage,
    ): Either<String, Unit> = either {
        val newPlayerState = methods.validationMethod(senderId to internalMessage).bind()
        methods.playerCoopStateSetter(newPlayerState)
        if (!newPlayerState.second.busy()) {
            interactionDataConnector.removeInteractionData(gameSessionId, senderId)
        }
        stopAdvertisingNotification(oldSenderState, gameSessionId)
        stopNegotiatingNotification(oldSenderState, senderId, gameSessionId)
    }

    private suspend fun cancelCoopForSecondPlayer(
        methods: CoopPAMethods,
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        secondPlayerId: PlayerId,
        internalMessage: CoopInternalMessages.SystemOutputMessage,
        outMessage: CoopMessages.CoopSystemOutputMessage
    ): Either<String, Unit> = either {
        val oldSecondPlayerState = methods.playerCoopStateGetter(secondPlayerId)
        if (oldSecondPlayerState is CoopStates.GatheringResources || oldSecondPlayerState is CoopStates.ResourcesDecide) {
            val newSecondPlayerState = methods.validationMethod(secondPlayerId to internalMessage).bind()
            methods.playerCoopStateSetter(newSecondPlayerState)
            if (!newSecondPlayerState.second.busy()) {
                interactionDataConnector.removeInteractionData(gameSessionId, secondPlayerId)
            }
            stopNegotiatingNotification(oldSecondPlayerState, secondPlayerId, gameSessionId)
            methods.interactionSendingMessages(senderId to outMessage)
            equipmentChangeProducer.sendMessage(
                gameSessionId,
                secondPlayerId,
                EquipmentInternalMessage.CheckEquipmentsForCoop
            )
        }
    }

    private suspend fun stopAdvertisingNotification(coopStates: CoopStates, gameSessionId: GameSessionId) {
        if (coopStates is CoopStates.WaitingForCompany && coopStates.isAdvertising) {
            interactionProducer.sendMessage(
                gameSessionId,
                coopStates.myId,
                CoopMessages.CoopSystemOutputMessage.StopAdvertisingCoop
            )
        }
    }

    private suspend fun stopNegotiatingNotification(
        coopStates: CoopStates,
        playerId: PlayerId,
        gameSessionId: GameSessionId
    ) {
        if (coopStates is CoopStates.ResourcesDecide) {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                CoopMessages.CoopSystemOutputMessage.NotificationCoopStop
            )
        }
    }
}
