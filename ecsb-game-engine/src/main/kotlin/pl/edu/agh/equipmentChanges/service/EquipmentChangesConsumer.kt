package pl.edu.agh.equipmentChanges.service

import arrow.core.*
import arrow.core.raise.option
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.redis.CoopStatesDataConnector
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.EquipmentChangeADT
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionConsumerCallback
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.Transactor
import java.time.LocalDateTime

class EquipmentChangesConsumer(
    private val coopInternalMessageProducer: InteractionProducer<CoopInternalMessages>,
    private val coopStatesDataConnector: CoopStatesDataConnector
) : InteractionConsumerCallback<EquipmentChangeADT> {
    private val logger by LoggerDelegate()
    override val tSerializer: KSerializer<EquipmentChangeADT> = EquipmentChangeADT.serializer()

    override fun consumeQueueName(hostTag: String) = "eq-change-$hostTag"

    override fun exchangeName(): String = InteractionProducer.EQ_CHANGE_EXCHANGE

    override fun bindQueues(channel: Channel, queueName: String) {
        // TODO use stable hashes Exchange type
        channel.exchangeDeclare(exchangeName(), BuiltinExchangeType.FANOUT)
        channel.queueDeclare(queueName, true, false, false, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }

    private suspend fun validateStates(
        gameSessionId: GameSessionId,
        firstPlayerId: PlayerId
    ): Option<Pair<CoopStates.ResourcesGathering, CoopStates.ResourcesGathering>> = option {
        val coopState = coopStatesDataConnector.getPlayerState(gameSessionId, firstPlayerId)
        val secondPlayerId = coopState.secondPlayer().bind()
        val secondPlayerState = coopStatesDataConnector.getPlayerState(gameSessionId, secondPlayerId)

        (
            if (coopState is CoopStates.ResourcesGathering && secondPlayerState is CoopStates.ResourcesGathering) {
                (coopState to secondPlayerState).some()
                    .filter { secondPlayerState.playerId == firstPlayerId }
                    .filter { coopState.playerId == secondPlayerId }
            } else {
                none()
            }
            ).bind()
    }

    private fun checkPlayerEquipment(
        coopStates: CoopStates.ResourcesGathering,
        playerId: PlayerId
    ): Option<PlayerEquipment> =
        coopStates.resourcesDecideValues.flatMap { (travelerPlayerId, resources) ->
            resources.mapValues { (_, value) -> value.toNonNeg() }
                .let { NonEmptyMap.fromMapSafe(it) }.map { resourcesValidated ->
                    PlayerEquipment(
                        0.nonNeg,
                        time = if (travelerPlayerId == playerId) 1.nonNeg else 0.nonNeg,
                        resources = resourcesValidated
                    )
                }
        }

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: EquipmentChangeADT
    ) {
        logger.info("[EQ-CHANGE] Player $senderId sent $message at $sentAt")
        option {
            val (coopState, secondPlayerState) = validateStates(gameSessionId, senderId).bind()
            logger.info("Found second player for resource gathering")
            val secondPlayerId = coopState.playerId
            println(secondPlayerState)
            println(coopState)

            Transactor.dbQuery {
                val senderEquipment = checkPlayerEquipment(coopState, senderId).bind()
                val secondPlayerEquipment = checkPlayerEquipment(secondPlayerState, secondPlayerId).bind()
                println(senderEquipment)
                println(secondPlayerEquipment)
                PlayerResourceDao.checkIfEquipmentsValid(
                    gameSessionId,
                    senderEquipment to senderId,
                    secondPlayerEquipment to secondPlayerId
                ).bind()
            }
            logger.info("Player equipment valid for travel 8)")
            coopInternalMessageProducer.sendMessage(
                gameSessionId,
                senderId,
                CoopInternalMessages.SystemInputMessage.ResourcesGathered(secondPlayerId)
            )
        }.onNone { logger.info("Error handling equipment change detected") }
    }
}
