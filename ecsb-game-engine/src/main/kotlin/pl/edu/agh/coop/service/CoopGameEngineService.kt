package pl.edu.agh.coop.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.rabbitmq.client.BuiltinExchangeType
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
import pl.edu.agh.equipment.domain.EquipmentChangeADT
import pl.edu.agh.interaction.service.InteractionConsumerCallback
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.nonEmptyMapOf
import pl.edu.agh.utils.susTupled2
import java.time.LocalDateTime

class CoopGameEngineService(
    private val coopStatesDataConnector: CoopStatesDataConnector,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>,
    private val equipmentChangeProducer: InteractionProducer<EquipmentChangeADT>
) : InteractionConsumerCallback<CoopInternalMessages> {
    private val logger by LoggerDelegate()

    override val tSerializer: KSerializer<CoopInternalMessages> = CoopInternalMessages.serializer()

    override fun consumeQueueName(hostTag: String): String = "coop-in-$hostTag"
    override fun exchangeName(): String = InteractionProducer.COOP_MESSAGES_EXCHANGE

    override fun bindQueues(channel: Channel, queueName: String) {
        // TODO use stable hashes Exchange type
        channel.exchangeDeclare(exchangeName(), BuiltinExchangeType.FANOUT)
        channel.queueDeclare(queueName, true, false, false, mapOf())
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
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateSetter = InteractionDataConnector()::setInteractionDataForPlayers.partially1(gameSessionId)

        val playerCoopStates = listOf(
            currentPlayerId to CoopInternalMessages.FindCoopAck(cityName, proposalSenderId),
            proposalSenderId to CoopInternalMessages.SystemInputMessage.FindCoopAck(cityName, currentPlayerId)
        ).traverse { validationMethod(it) }
            .flatMap { if (currentPlayerId == proposalSenderId) Either.Left("Same receiver") else Either.Right(it) }
            .bind()

        val playerStates = nonEmptyMapOf(
            currentPlayerId to InteractionStatus.COOP_BUSY,
            proposalSenderId to InteractionStatus.COOP_BUSY
        )
        ensure(interactionStateSetter(playerStates)) { logger.error("Player busy already :/"); "Player busy" }
        playerCoopStates.forEach { playerCoopStateSetter(it) }

        listOf(
            proposalSenderId to CoopMessages.CoopSystemInputMessage.CancelCoopAtAnyStage,
            proposalSenderId to ChatMessageADT.SystemInputMessage.NotificationCoopStart(proposalSenderId),
            currentPlayerId to ChatMessageADT.SystemInputMessage.NotificationCoopStart(currentPlayerId)
        ).forEach { interactionSendingMessages(it) }
    }

    private suspend fun proposeCoopWithTravel(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        travelName: TravelName
    ): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateSetter = InteractionDataConnector()::setInteractionData.partially1(gameSessionId)::susTupled2

        Transactor
            .dbQuery { TravelDao.getTravelByName(gameSessionId, travelName) }
            .toEither { "Travel with name $travelName not found in $gameSessionId" }
            .bind()

        val newPlayerStatus = validationMethod(senderId to CoopInternalMessages.FindCoop(travelName)).bind()

        ensure(interactionStateSetter(senderId to InteractionStatus.COOP_BUSY)) {
            logger.error("Player busy already :/")
            "You are busy idiot"
        }
        playerCoopStateSetter(newPlayerStatus)

        interactionSendingMessages(
            senderId to CoopMessages.CoopSystemInputMessage.SearchingForCoop(
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
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateSetter = InteractionDataConnector()::setInteractionData.partially1(gameSessionId)::susTupled2

        val newStates = listOf(
            senderId to CoopInternalMessages.ProposeCoop(receiverId),
            receiverId to CoopInternalMessages.SystemInputMessage.ProposeCoop
        ).traverse { validationMethod(it) }
            .flatMap { if (senderId == receiverId) Either.Left("Same receiver") else Either.Right(it) }
            .bind()
            .map { playerCoopStateSetter(it); it }

        newStates.forEach { (player, state) ->
            if (state.busy()) {
                interactionStateSetter(player to InteractionStatus.COOP_BUSY)
            } else {
                interactionStateSetter(player to InteractionStatus.NOT_BUSY)
            }
        }

        interactionSendingMessages(senderId to CoopMessages.CoopSystemInputMessage.ProposeCoop(receiverId))
    }

    private suspend fun acceptCoopWithPlayer(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        proposalSenderId: PlayerId
    ): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateSetter = InteractionDataConnector()::setInteractionData.partially1(gameSessionId)::susTupled2

        val newStates = listOf(
            senderId to CoopInternalMessages.ProposeCoopAck(proposalSenderId),
            proposalSenderId to CoopInternalMessages.SystemInputMessage.ProposeCoopAck(senderId)
        ).traverse { validationMethod(it) }.bind().map { playerCoopStateSetter(it); it }

        newStates.forEach { (player, state) ->
            if (state.busy()) {
                interactionStateSetter(player to InteractionStatus.COOP_BUSY)
            } else {
                interactionStateSetter(player to InteractionStatus.NOT_BUSY)
            }
        }

        listOf(
            senderId to CoopMessages.CoopSystemInputMessage.ProposeCoopAck(proposalSenderId),
            senderId to ChatMessageADT.SystemInputMessage.NotificationCoopStart(senderId),
            proposalSenderId to ChatMessageADT.SystemInputMessage.NotificationCoopStart(proposalSenderId)
        ).forEach { interactionSendingMessages(it) }
    }

    private suspend fun acceptResourceValues(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        resourcesDecideValues: ResourcesDecideValues
    ): Either<String, Unit> =
        either {
            val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
            val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
            val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
            val interactionStateSetter =
                InteractionDataConnector()::setInteractionData.partially1(gameSessionId)::susTupled2

            val secondPlayerId = coopStatesDataConnector
                .getPlayerState(gameSessionId, senderId)
                .secondPlayer()
                .toEither { "Second player for coop not found" }
                .bind()

            val playerState = listOf(
                secondPlayerId to CoopInternalMessages.SystemInputMessage.ResourcesDecideAck(resourcesDecideValues),
                senderId to CoopInternalMessages.ResourcesDecideAck(resourcesDecideValues)
            ).traverse { validationMethod(it) }
                .onRight { result ->
                    result.forEach { playerCoopStateSetter(it) }
                }
                .bind()

            playerState.forEach { (player, state) ->
                if (state.busy()) {
                    interactionStateSetter(player to InteractionStatus.COOP_BUSY)
                    interactionSendingMessages(player to ChatMessageADT.SystemInputMessage.NotificationCoopStart(player))
                } else {
                    InteractionDataConnector().removeInteractionData(gameSessionId, player)
                    interactionSendingMessages(player to ChatMessageADT.SystemInputMessage.NotificationCoopStop(player))
                }
            }
            playerState.firstOrNone().map { (playerId, state) ->
                if (state is CoopStates.ResourcesGathering) {
                    equipmentChangeProducer.sendMessage(
                        gameSessionId,
                        playerId,
                        EquipmentChangeADT.CheckEquipmentForTrade
                    )
                    logger.info("Sent message to check player resources for travel in coop")
                }
            }

            interactionSendingMessages(
                senderId to CoopMessages.CoopSystemInputMessage.ResourceDecideAck(
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
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2

        val secondPlayerId = coopStatesDataConnector
            .getPlayerState(gameSessionId, senderId)
            .secondPlayer()
            .toEither { "Second player for coop not found" }
            .bind()

        listOf(
            secondPlayerId to CoopInternalMessages.SystemInputMessage.ResourcesDecide(resourcesDecideValues),
            senderId to CoopInternalMessages.ResourcesDecide(resourcesDecideValues)
        ).traverse { validationMethod(it) }
            .onRight { result ->
                result.forEach { playerCoopStateSetter(it) }
            }
            .bind()

        interactionSendingMessages(
            senderId to CoopMessages.CoopSystemInputMessage.ResourceDecide(
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
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2

        val secondPlayerId = coopStatesDataConnector
            .getPlayerState(gameSessionId, senderId)
            .secondPlayer()
            .toEither { "Second player for coop not found" }
            .bind()

        Transactor
            .dbQuery { TravelDao.getTravelByName(gameSessionId, travelName) }
            .toEither { "Travel with name $travelName not found in $gameSessionId" }
            .bind()

        val newStates = listOf(
            senderId to CoopInternalMessages.CityVoteAck(travelName),
            secondPlayerId to CoopInternalMessages.SystemInputMessage.CityVoteAck(travelName)
        ).traverse { validationMethod(it) }.bind()

        newStates.forEach { playerCoopStateSetter(it) }

        interactionSendingMessages(
            senderId,
            CoopMessages.CoopSystemInputMessage.CityDecideAck(travelName, secondPlayerId)
        )
    }

    private suspend fun changeCityVotes(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        currentVotes: CityDecideVotes
    ): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2

        val secondPlayerId = coopStatesDataConnector
            .getPlayerState(gameSessionId, senderId)
            .secondPlayer()
            .toEither { "Second player for coop not found" }
            .bind()

        val travelNames = currentVotes.flatMap { it.map.keys.toNonEmptySetOrNone() }
        travelNames.traverse {
            val actualSize = it.size
            Transactor.dbQuery { TravelDao.getTravelByNames(gameSessionId, it) }
                .map { travels -> travels.size == actualSize }
                .toEither { "Travel names in city decide votes didn't match these in $gameSessionId" }
        }.bind()

        val newStates = listOf(
            senderId to CoopInternalMessages.CityVotes(currentVotes),
            secondPlayerId to CoopInternalMessages.SystemInputMessage.CityVotes
        ).traverse { validationMethod(it) }.bind()

        newStates.forEach { playerCoopStateSetter(it) }

        interactionSendingMessages(
            senderId to CoopMessages.CoopSystemInputMessage.CityDecide(currentVotes, secondPlayerId)
        )
    }

    private suspend fun resourceGathered(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        secondPlayerId: PlayerId
    ): Either<String, Unit> =
        either {
            val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
            val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)
            val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2

            val senderNewState = validationMethod(senderId to CoopInternalMessages.SystemInputMessage.ResourcesGathered(secondPlayerId)).bind()
            val secondPlayerNewState = validationMethod(secondPlayerId to CoopInternalMessages.SystemInputMessage.ResourcesGathered(senderId)).bind()

            playerCoopStateSetter(senderNewState)
            playerCoopStateSetter(secondPlayerNewState)

            senderNewState.let { (playerId, state) ->
                when (state) {
                    is CoopStates.WaitingForCoopEnd ->
                        interactionSendingMessages(playerId, CoopMessages.CoopSystemInputMessage.WaitForCoopEnd(secondPlayerId, state.travelName))
                    is CoopStates.ActiveTravelPlayer ->
                        interactionSendingMessages(playerId, CoopMessages.CoopSystemInputMessage.GoToGateAndTravel(senderId, state.travelName))
                    else -> logger.error("This state should not be here")
                }
            }
        }

    private suspend fun cancelCoop(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateDelete = InteractionDataConnector()::removeInteractionData.partially1(gameSessionId)

        val maybeSecondPlayerId = coopStatesDataConnector.getPlayerState(gameSessionId, senderId).secondPlayer()

        val playerStates = listOf(senderId.some(), maybeSecondPlayerId)
            .flattenOption()
            .map { it to CoopInternalMessages.CancelCoopAtAnyStage }
            .traverse { validationMethod(it) }.bind()

        playerStates.forEach { playerCoopStateSetter(it) }

        interactionStateDelete(senderId)
        interactionSendingMessages(senderId to CoopMessages.CoopSystemInputMessage.CancelCoopAtAnyStage)

        maybeSecondPlayerId
            .onSome {
                interactionStateDelete(senderId)
                interactionSendingMessages(senderId to CoopMessages.CoopSystemInputMessage.CancelCoopAtAnyStage)
            }
    }
}
