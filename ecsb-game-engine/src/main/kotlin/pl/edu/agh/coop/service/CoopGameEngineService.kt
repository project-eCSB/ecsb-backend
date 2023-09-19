package pl.edu.agh.coop.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.coop.domain.CityDecideVotes
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.coop.redis.CoopStatesDataConnector
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.nonEmptyMapOf
import pl.edu.agh.utils.susTupled2
import java.time.LocalDateTime

class CoopGameEngineService(
    private val coopStatesDataConnector: CoopStatesDataConnector,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>,
    private val interactionDataConnector: InteractionDataService = InteractionDataService.instance,
    private val travelCoopService: TravelCoopService = TravelCoopService.instance
) : InteractionConsumer<CoopInternalMessages> {
    private val logger by LoggerDelegate()

    private inner class CoopPAMethods(gameSessionId: GameSessionId) {

        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2

        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2

        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2

        val interactionStateSetter = interactionDataConnector::setInteractionData.partially1(gameSessionId)::susTupled2
    }

    override val tSerializer: KSerializer<CoopInternalMessages> = CoopInternalMessages.serializer()

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
        message: CoopInternalMessages
    ) {
        logger.info("Got message from $gameSessionId $senderId sent at $sentAt ($message)")
        when (message) {
            is CoopInternalMessages.FindCoop -> proposeCoopWithTravel(gameSessionId, senderId, message.cityName)
            is CoopInternalMessages.FindCoopAck -> acceptCoopWithTravel(
                gameSessionId,
                senderId,
                message.cityName,
                message.proposalSenderId
            )

            is CoopInternalMessages.ProposeCoop -> proposeCoopToPlayer(gameSessionId, senderId, message.receiverId)
            is CoopInternalMessages.ProposeCoopAck -> acceptCoopWithPlayer(
                gameSessionId,
                senderId,
                message.proposalSenderId
            )

            is CoopInternalMessages.ResourcesDecide -> changeResourcesValues(
                gameSessionId,
                senderId,
                message.resourcesDecideValues
            )

            is CoopInternalMessages.ResourcesDecideAck -> acceptResourceValues(
                gameSessionId,
                senderId,
                message.resourcesDecideValues
            )

            is CoopInternalMessages.CityVotes -> changeCityVotes(gameSessionId, senderId, message.currentVotes)

            is CoopInternalMessages.CityVoteAck -> tryToAcceptVotes(gameSessionId, senderId, message.travelName)

            CoopInternalMessages.CancelCoopAtAnyStage -> cancelCoop(gameSessionId, senderId)

            is CoopInternalMessages.SystemInputMessage.ResourcesGathered -> resourceGathered(
                gameSessionId,
                senderId,
                message.secondPlayerId
            )

            CoopInternalMessages.RenegotiateCityRequest -> renegotiateCity(gameSessionId, senderId)
            CoopInternalMessages.RenegotiateResourcesRequest -> renegotiateResources(gameSessionId, senderId)

            CoopInternalMessages.SystemInputMessage.EndOfTravelReady -> TODO()
            CoopInternalMessages.SystemInputMessage.TravelDone -> TODO()

            else -> Either.Left("Not implemented yet")
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

    private suspend fun acceptCoopWithTravel(
        gameSessionId: GameSessionId,
        currentPlayerId: PlayerId,
        cityName: TravelName,
        proposalSenderId: PlayerId
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val playerCoopStates = listOf(
            currentPlayerId to CoopInternalMessages.FindCoopAck(cityName, proposalSenderId),
            proposalSenderId to CoopInternalMessages.SystemInputMessage.FindCoopAck(cityName, currentPlayerId)
        ).traverse { methods.validationMethod(it) }
            .flatMap { if (currentPlayerId == proposalSenderId) Either.Left("Same receiver") else Either.Right(it) }
            .bind()

        val playerStates = nonEmptyMapOf(
            currentPlayerId to InteractionStatus.COOP_BUSY,
            proposalSenderId to InteractionStatus.COOP_BUSY
        )
        ensure(
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                playerStates
            )
        ) { logger.error("Player busy already :/"); "Player busy" }
        playerCoopStates.forEach { methods.playerCoopStateSetter(it) }

        listOf(
            proposalSenderId to CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage,
            proposalSenderId to ChatMessageADT.SystemOutputMessage.NotificationCoopStart(proposalSenderId),
            currentPlayerId to ChatMessageADT.SystemOutputMessage.NotificationCoopStart(currentPlayerId)
        ).forEach { methods.interactionSendingMessages(it) }
    }

    private suspend fun proposeCoopWithTravel(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        travelName: TravelName
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        travelCoopService.getTravelByName(gameSessionId, travelName)
            .toEither { "Travel with name $travelName not found in $gameSessionId" }
            .bind()

        val newPlayerStatus =
            methods.validationMethod(senderId to CoopInternalMessages.FindCoop(travelName)).bind()

        ensure(methods.interactionStateSetter(senderId to InteractionStatus.COOP_BUSY)) {
            logger.error("Player busy already :/")
            "You are busy idiot"
        }
        methods.playerCoopStateSetter(newPlayerStatus)

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.SearchingForCoop(
                travelName,
                senderId
            )
        )
    }

    private suspend fun proposeCoopToPlayer(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val newStates = listOf(
            senderId to CoopInternalMessages.ProposeCoop(receiverId),
            receiverId to CoopInternalMessages.SystemInputMessage.ProposeCoop
        ).traverse { methods.validationMethod(it) }
            .flatMap { if (senderId == receiverId) Either.Left("Same receiver") else Either.Right(it) }
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
            senderId to CoopMessages.CoopSystemOutputMessage.ProposeCoop(
                receiverId
            )
        )
    }

    private suspend fun acceptCoopWithPlayer(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        proposalSenderId: PlayerId
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val newStates = listOf(
            senderId to CoopInternalMessages.ProposeCoopAck(proposalSenderId),
            proposalSenderId to CoopInternalMessages.SystemInputMessage.ProposeCoopAck(senderId)
        ).traverse { methods.validationMethod(it) }.bind()
            .map { methods.playerCoopStateSetter(it); it }

        newStates.forEach { (player, state) ->
            if (state.busy()) {
                methods.interactionStateSetter(player to InteractionStatus.COOP_BUSY)
            } else {
                methods.interactionStateSetter(player to InteractionStatus.NOT_BUSY)
            }
        }

        listOf(
            senderId to CoopMessages.CoopSystemOutputMessage.ProposeCoopAck(proposalSenderId),
            senderId to ChatMessageADT.SystemOutputMessage.NotificationCoopStart(senderId),
            proposalSenderId to ChatMessageADT.SystemOutputMessage.NotificationCoopStart(proposalSenderId)
        ).forEach { methods.interactionSendingMessages(it) }
    }

    private suspend fun acceptResourceValues(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        resourcesDecideValues: ResourcesDecideValues
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val secondPlayerId = coopStatesDataConnector
            .getPlayerState(gameSessionId, senderId)
            .secondPlayer()
            .toEither { "Second player for coop not found" }
            .bind()

        val playerState = listOf(
            secondPlayerId to CoopInternalMessages.SystemInputMessage.ResourcesDecideAck(resourcesDecideValues),
            senderId to CoopInternalMessages.ResourcesDecideAck(resourcesDecideValues)
        ).traverse { methods.validationMethod(it) }
            .onRight { result ->
                result.forEach { methods.playerCoopStateSetter(it) }
            }
            .bind()

        playerState.forEach { (player, state) ->
            if (state.busy()) {
                methods.interactionStateSetter(player to InteractionStatus.COOP_BUSY)
                methods.interactionSendingMessages(
                    player to ChatMessageADT.SystemOutputMessage.NotificationCoopStart(
                        player
                    )
                )
            } else {
                interactionDataConnector.removeInteractionData(gameSessionId, player)
                methods.interactionSendingMessages(
                    player to ChatMessageADT.SystemOutputMessage.NotificationCoopStop(
                        player
                    )
                )
            }
        }
        playerState.firstOrNone().map { (playerId, state) ->
            if (state is CoopStates.ResourcesGathering) {
                equipmentChangeProducer.sendMessage(
                    gameSessionId,
                    playerId,
                    EquipmentInternalMessage.CheckEquipmentForTrade
                )
                logger.info("Sent message to check player resources for travel in coop")
            }
        }

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceDecideAck(
                resourcesDecideValues,
                secondPlayerId
            )
        )
    }

    private suspend fun changeResourcesValues(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        resourcesDecideValues: ResourcesDecideValues
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val secondPlayerId = coopStatesDataConnector
            .getPlayerState(gameSessionId, senderId)
            .secondPlayer()
            .toEither { "Second player for coop not found" }
            .bind()

        listOf(
            secondPlayerId to CoopInternalMessages.SystemInputMessage.ResourcesDecide(resourcesDecideValues),
            senderId to CoopInternalMessages.ResourcesDecide(resourcesDecideValues)
        ).traverse { methods.validationMethod(it) }
            .onRight { result ->
                result.forEach { methods.playerCoopStateSetter(it) }
            }
            .bind()

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.ResourceDecide(
                resourcesDecideValues,
                secondPlayerId
            )
        )
    }

    private suspend fun tryToAcceptVotes(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        travelName: TravelName
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val secondPlayerId = coopStatesDataConnector
            .getPlayerState(gameSessionId, senderId)
            .secondPlayer()
            .toEither { "Second player for coop not found" }
            .bind()

        travelCoopService.getTravelByName(gameSessionId, travelName)
            .toEither { "Travel with name $travelName not found in $gameSessionId" }
            .bind()

        val newStates = listOf(
            senderId to CoopInternalMessages.CityVoteAck(travelName),
            secondPlayerId to CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName)
        ).traverse { methods.validationMethod(it) }.bind()

        newStates.forEach { methods.playerCoopStateSetter(it) }

        methods.interactionSendingMessages(
            senderId to
                    CoopMessages.CoopSystemOutputMessage.CityDecideAck(travelName, secondPlayerId)
        )
    }

    private suspend fun changeCityVotes(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        currentVotes: CityDecideVotes
    ): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val secondPlayerId = coopStatesDataConnector
            .getPlayerState(gameSessionId, senderId)
            .secondPlayer()
            .toEither { "Second player for coop not found" }
            .bind()

        val travelNames = currentVotes.flatMap { it.map.keys.toNonEmptySetOrNone() }
        travelNames.traverse {
            val actualSize = it.size
            travelCoopService.getTravelByNames(gameSessionId, it)
                .map { travels -> travels.size == actualSize }
                .toEither { "Travel names in city decide votes didn't match these in $gameSessionId" }
        }.bind()

        val newStates = listOf(
            senderId to CoopInternalMessages.CityVotes(currentVotes),
            secondPlayerId to CoopInternalMessages.SystemInputMessage.CityVotes
        ).traverse { methods.validationMethod(it) }.bind()

        newStates.forEach { methods.playerCoopStateSetter(it) }

        methods.interactionSendingMessages(
            senderId to CoopMessages.CoopSystemOutputMessage.CityDecide(currentVotes, secondPlayerId)
        )
    }

    private suspend fun renegotiateResources(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> =
        either {
            val methods = CoopPAMethods(gameSessionId)

            val secondPlayerId = coopStatesDataConnector
                .getPlayerState(gameSessionId, senderId)
                .secondPlayer()
                .toEither { "Second player for coop not found" }
                .bind()

            val newStates = listOf(
                senderId to CoopInternalMessages.RenegotiateResourcesRequest,
                secondPlayerId to CoopInternalMessages.SystemInputMessage.RenegotiateResourcesRequest
            ).traverse { methods.validationMethod(it) }.bind()

            val playerStates = nonEmptyMapOf<PlayerId, InteractionStatus>(
                senderId to InteractionStatus.COOP_BUSY,
                secondPlayerId to InteractionStatus.COOP_BUSY
            )
            ensure(
                interactionDataConnector.setInteractionDataForPlayers(
                    gameSessionId,
                    playerStates
                )
            ) { logger.error("Player busy already :/"); "Player busy" }
            newStates.forEach { methods.playerCoopStateSetter(it) }

            listOf<Pair<PlayerId, CoopMessages.CoopSystemOutputMessage>>(
                senderId to CoopMessages.CoopSystemOutputMessage.RenegotiateResourcesRequest,
                secondPlayerId to CoopMessages.CoopSystemOutputMessage.RenegotiateResourcesRequest
            ).forEach {
                methods.interactionSendingMessages(it)
            }
        }

    private suspend fun renegotiateCity(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> =
        either {
            val methods = CoopPAMethods(gameSessionId)

            val secondPlayerId = coopStatesDataConnector
                .getPlayerState(gameSessionId, senderId)
                .secondPlayer()
                .toEither { "Second player for coop not found" }
                .bind()

            val newStates = listOf(
                senderId to CoopInternalMessages.RenegotiateCityRequest,
                secondPlayerId to CoopInternalMessages.SystemInputMessage.RenegotiateCityRequest
            ).traverse { methods.validationMethod(it) }.bind()

            val playerStates = nonEmptyMapOf<PlayerId, InteractionStatus>(
                senderId to InteractionStatus.COOP_BUSY,
                secondPlayerId to InteractionStatus.COOP_BUSY
            )
            ensure(
                interactionDataConnector.setInteractionDataForPlayers(
                    gameSessionId,
                    playerStates
                )
            ) { logger.error("Player busy already :/"); "Player busy" }
            newStates.forEach { methods.playerCoopStateSetter(it) }

            listOf<Pair<PlayerId, CoopMessages.CoopSystemOutputMessage>>(
                senderId to CoopMessages.CoopSystemOutputMessage.RenegotiateCityRequest,
                secondPlayerId to CoopMessages.CoopSystemOutputMessage.RenegotiateCityRequest
            ).forEach {
                methods.interactionSendingMessages(it)
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
                senderId to CoopInternalMessages.SystemInputMessage.ResourcesGathered(
                    secondPlayerId
                )
            ).bind()
        val secondPlayerNewState =
            methods.validationMethod(
                secondPlayerId to CoopInternalMessages.SystemInputMessage.ResourcesGathered(
                    senderId
                )
            ).bind()

        methods.playerCoopStateSetter(senderNewState)
        methods.playerCoopStateSetter(secondPlayerNewState)

        senderNewState.let { (playerId, state) ->
            when (state) {
                is CoopStates.WaitingForCoopEnd ->
                    methods.interactionSendingMessages(
                        playerId to
                                CoopMessages.CoopSystemOutputMessage.WaitForCoopEnd(secondPlayerId, state.travelName)
                    )

                is CoopStates.ActiveTravelPlayer ->
                    methods.interactionSendingMessages(
                        playerId to
                                CoopMessages.CoopSystemOutputMessage.GoToGateAndTravel(senderId, state.travelName)
                    )

                else -> logger.error("This state should not be here")
            }
        }
    }

    private suspend fun cancelCoop(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> = either {
        val methods = CoopPAMethods(gameSessionId)
        val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)
        val maybeSecondPlayerId = coopStatesDataConnector.getPlayerState(gameSessionId, senderId).secondPlayer()

        val playerStates = listOf(senderId.some(), maybeSecondPlayerId)
            .flattenOption()
            .map { it to CoopInternalMessages.CancelCoopAtAnyStage }
            .traverse { methods.validationMethod(it) }.bind()

        playerStates.forEach { methods.playerCoopStateSetter(it) }

        interactionStateDelete(senderId)
        methods.interactionSendingMessages(senderId to CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage)

        maybeSecondPlayerId
            .onSome {
                interactionStateDelete(senderId)
                methods.interactionSendingMessages(
                    senderId to CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage
                )
            }
    }
}
