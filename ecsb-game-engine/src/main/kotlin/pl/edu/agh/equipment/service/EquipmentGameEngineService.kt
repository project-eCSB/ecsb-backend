package pl.edu.agh.equipment.service

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
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.nonEmptyMapOf
import java.time.LocalDateTime

typealias ParZipFunction = suspend CoroutineScope.() -> Unit

class EquipmentGameEngineService(
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
        firstPlayerState: CoopStates
    ): Option<CoopStates.GatheringResources> = option {
        val secondPlayerId = firstPlayerState.secondPlayer().bind()
        val secondPlayerState = coopStatesDataConnector.getPlayerState(gameSessionId, secondPlayerId)

        if (firstPlayerState is CoopStates.GatheringResources && secondPlayerState is CoopStates.GatheringResources) {
            secondPlayerState.some().filter { secondPlayerState.secondPlayer().bind() == firstPlayerState.myId }.bind()
        } else {
            none<CoopStates.GatheringResources>().bind()
        }
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
                        PlayerIdConst.ECSB_CHAT_PLAYER_ID,
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
                    PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                    ChatMessageADT.SystemOutputMessage.PlayerResourceChanged(senderId, resources)
                )
            }
        }

        val coopEquipmentAction: ParZipFunction = {
            option {
                val coopState = coopStatesDataConnector.getPlayerState(gameSessionId, senderId)
                if (coopState is CoopStates.GatheringResources && coopState.secondPlayer().isSome()) {
                    val secondPlayerState = validateStates(gameSessionId, coopState).bind()
                    logger.info("Found second player for resource gathering")
                    val secondPlayerId = coopState.secondPlayer().bind()

                    val (senderCoopEquipment, secondPlayerCoopEquipment) = Transactor.dbQuery {
                        val senderNegotiatedResources = coopState.negotiatedBid.map { it.second.resources }.bind()
                        val secondPlayerNegotiatedResources =
                            secondPlayerState.negotiatedBid.map { it.second.resources }.bind()

                        val equipments =
                            PlayerResourceDao.getUsersEquipments(gameSessionId, listOf(senderId, secondPlayerId))

                        val senderCoopEquipment = equipments.getOrNone(senderId).map {
                            CoopPlayerEquipment.invoke(it.resources, senderNegotiatedResources)
                        }.bind()

                        val secondPlayerCoopEquipment = equipments.getOrNone(secondPlayerId).map {
                            CoopPlayerEquipment.invoke(it.resources, secondPlayerNegotiatedResources)
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
                            CoopInternalMessages.UserInputMessage.ResourcesGatheredUser(
                                secondPlayerId.some(),
                                nonEmptyMapOf(
                                    senderId to senderCoopEquipment,
                                    secondPlayerId to secondPlayerCoopEquipment
                                )
                            )
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
                } else if (coopState is CoopStates.GatheringResources || coopState is CoopStates.WaitingForCompany) {
                    val senderCoopEquipment = Transactor.dbQuery {
                        val equipment = PlayerResourceDao.getUserEquipment(gameSessionId, senderId).bind()
                        val travelCosts =
                            TravelDao.getTravelCostsByName(gameSessionId, coopState.travelName().bind()).bind()
                        CoopPlayerEquipment.invoke(equipment.resources, travelCosts)
                    }

                    senderCoopEquipment.validate().mapLeft {
                        logger.info("Not enough resources for player $senderId, $it")
                        logger.info("Player equipment not valid for travel ;)")
                        coopInternalMessageProducer.sendMessage(
                            gameSessionId,
                            senderId,
                            CoopInternalMessages.UserInputMessage.ResourcesUnGatheredSingleUser(
                                senderCoopEquipment
                            )
                        )
                    }.map {
                        logger.info("Player equipment valid for travel 8)")
                        coopInternalMessageProducer.sendMessage(
                            gameSessionId,
                            senderId,
                            CoopInternalMessages.UserInputMessage.ResourcesGatheredUser(
                                none(),
                                nonEmptyMapOf(
                                    senderId to senderCoopEquipment,
                                )
                            )
                        )
                    }
                }
            }.onNone { logger.info("Error handling equipment change detected") }
        }

        parZip(equipmentChangeAction, coopEquipmentAction, tokensUsedAction) { _, _, _ -> }
    }
}
