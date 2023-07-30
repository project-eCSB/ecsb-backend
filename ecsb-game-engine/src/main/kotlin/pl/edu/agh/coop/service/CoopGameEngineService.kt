package pl.edu.agh.coop.service

import arrow.core.*
import arrow.core.raise.either
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.redis.CoopStatesDataConnector
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionConsumerCallback
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.susTupled2
import java.time.LocalDateTime

class CoopGameEngineService(
    private val coopStatesDataConnector: CoopStatesDataConnector,
    private val interactionDataConnector: InteractionDataConnector,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>
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

            CoopInternalMessages.CancelCoopAtAnyStage -> cancelCoop(gameSessionId, senderId)

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
        val interactionStateSetter = interactionDataConnector::setInteractionData.partially1(gameSessionId)::susTupled2

        val playerCoopStates = listOf(
            currentPlayerId to CoopInternalMessages.FindCoopAck(cityName, proposalSenderId),
            proposalSenderId to CoopInternalMessages.SystemInputMessage.FindCoopAck(cityName, currentPlayerId)
        ).traverse { validationMethod(it) }.bind()
        playerCoopStates.forEach { playerCoopStateSetter(it) }

        val playerStates = listOf(
            currentPlayerId to InteractionStatus.BUSY,
            proposalSenderId to InteractionStatus.BUSY
        )
        playerStates.forEach { interactionStateSetter(it) }

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
        val interactionStateSetter = interactionDataConnector::setInteractionData.partially1(gameSessionId)::susTupled2

        val newPlayerStatus = validationMethod(senderId to CoopInternalMessages.FindCoop(travelName)).bind()

        playerCoopStateSetter(newPlayerStatus)
        interactionStateSetter(senderId to InteractionStatus.BUSY)

        interactionSendingMessages(
            senderId to CoopMessages.CoopSystemInputMessage.SearchingForCoop(
                travelName,
                senderId
            )
        )
    }

    private suspend fun cancelCoop(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerCoopStateSetter = coopStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)

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
