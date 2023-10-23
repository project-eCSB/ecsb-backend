package pl.edu.agh.equipmentChanges.service

import arrow.core.Option
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.raise.option
import arrow.core.some
import arrow.fx.coroutines.parZip
import com.rabbitmq.client.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TimeMessages
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.redis.CoopStatesDataConnector
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.Money
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.nonEmptyMapOf
import java.time.LocalDateTime

typealias ParZipFunction = suspend CoroutineScope.() -> Unit

class EquipmentChangesConsumer(
    private val coopInternalMessageProducer: InteractionProducer<CoopInternalMessages.UserInputMessage>,
    private val interactionMessageProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val coopStatesDataConnector: CoopStatesDataConnector
) : InteractionConsumer<EquipmentInternalMessage> {
    private val logger by LoggerDelegate()
    override val tSerializer: KSerializer<EquipmentInternalMessage> = EquipmentInternalMessage.serializer()

    override fun consumeQueueName(hostTag: String) = "eq-change-$hostTag"

    override fun exchangeName(): String = InteractionProducer.EQ_CHANGE_EXCHANGE

    override fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), ExchangeType.SHARDING.value)
        channel.queueDeclare(queueName, true, false, true, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }

    private suspend fun validateStates(
        gameSessionId: GameSessionId,
        firstPlayerId: PlayerId
    ): Option<Pair<CoopStates.GatheringResources, CoopStates.GatheringResources>> = option {
        val coopState = coopStatesDataConnector.getPlayerState(gameSessionId, firstPlayerId)
        val secondPlayerId = coopState.secondPlayer().bind()
        val secondPlayerState = coopStatesDataConnector.getPlayerState(gameSessionId, secondPlayerId)

        if (coopState is CoopStates.GatheringResources && secondPlayerState is CoopStates.GatheringResources) {
            (coopState to secondPlayerState).some()
                .filter { secondPlayerState.secondPlayer().bind() == firstPlayerId }
                .filter { coopState.secondPlayer().bind() == secondPlayerId }.bind()
        } else {
            none<Pair<CoopStates.GatheringResources, CoopStates.GatheringResources>>().bind()
        }
    }

    /*
     * We don't check time anymore
     */
    private fun checkPlayerEquipment(coopStates: CoopStates.GatheringResources): Option<PlayerEquipment> =
        coopStates.negotiatedBid.map {
            PlayerEquipment(
                Money(0),
                it.second.resources
            )
        }

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: EquipmentInternalMessage
    ) {
        logger.info("[EQ-CHANGE] Player $senderId sent $message at $sentAt")
        val tokensUsedAction: ParZipFunction = {
            if (message is EquipmentInternalMessage.EquipmentChangeDetected) {
                message.updatedResources.timeTokensUsed.map { tokensUsed ->
                    interactionMessageProducer.sendMessage(
                        gameSessionId,
                        senderId,
                        TimeMessages.TimeSystemOutputMessage.PlayerTokensRefresh(senderId, tokensUsed)
                    )
                }
            } else {
                logger.info("Message is not equipment change detected, not checking time")
            }
        }

        val equipmentChangeAction: ParZipFunction = {
            option {
                val resources = Transactor.dbQuery {
                    PlayerResourceDao.getUserEquipment(gameSessionId, senderId)
                }.bind()
                logger.info("Sending new equipment to player $senderId in $gameSessionId")
                interactionMessageProducer.sendMessage(
                    gameSessionId,
                    senderId,
                    ChatMessageADT.SystemOutputMessage.PlayerResourceChanged(resources)
                )
            }
        }
        val coopEquipmentAction: ParZipFunction = {
            option {
                val (coopState, secondPlayerState) = validateStates(gameSessionId, senderId).bind()
                logger.info("Found second player for resource gathering")
                val secondPlayerId = coopState.secondPlayer().bind()
                println(secondPlayerState)
                println(coopState)

                val (senderCoopEquipment, secondPlayerCoopEquipment) = Transactor.dbQuery {
                    val senderEquipment = checkPlayerEquipment(coopState).bind()
                    val secondPlayerEquipment = checkPlayerEquipment(secondPlayerState).bind()

                    val equipments =
                        PlayerResourceDao.getUsersEquipments(gameSessionId, listOf(senderId, secondPlayerId))

                    val senderCoopEquipment = equipments.getOrNone(senderId).map {
                        CoopPlayerEquipment.invoke(senderEquipment, it)
                    }.bind()

                    val secondPlayerCoopEquipment = equipments.getOrNone(secondPlayerId).map {
                        CoopPlayerEquipment.invoke(secondPlayerEquipment, it)
                    }.bind()

                    senderCoopEquipment to secondPlayerCoopEquipment
                }

                senderCoopEquipment.validate().mapLeft {
                    logger.info("Not enough resources for player $senderId, $it")
                }
                secondPlayerCoopEquipment.validate().mapLeft {
                    logger.info("Not enough resources for player $secondPlayerId, $it")
                }

                if (senderCoopEquipment.validate().isRight() && secondPlayerCoopEquipment.validate().isRight()) {
                    logger.info("Player equipment valid for travel 8)")
                    coopInternalMessageProducer.sendMessage(
                        gameSessionId,
                        senderId,
                        CoopInternalMessages.UserInputMessage.ResourcesGatheredUser(secondPlayerId)
                    )
                } else {
                    logger.info("Player equipment not valid for travel ;)")
                    coopInternalMessageProducer.sendMessage(
                        gameSessionId,
                        senderId,
                        CoopInternalMessages.UserInputMessage.ResourcesUnGatheredUser(
                            secondPlayerId,
                            nonEmptyMapOf(
                                senderId to senderCoopEquipment,
                                secondPlayerId to secondPlayerCoopEquipment
                            )
                        )
                    )
                }
            }.onNone { logger.info("Error handling equipment change detected") }
        }

        parZip(equipmentChangeAction, coopEquipmentAction, tokensUsedAction) { _, _, _ -> }
    }
}
