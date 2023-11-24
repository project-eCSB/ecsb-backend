package pl.edu.agh.equipment.service

import arrow.core.*
import arrow.core.raise.option
import arrow.fx.coroutines.parZip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TimeMessages
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.domain.TimeTokensCoopInfo
import pl.edu.agh.coop.redis.CoopStatesDataConnector
import pl.edu.agh.domain.AmountDiff
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.time.dao.PlayerTimeTokenDao
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
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
    override fun exchangeType(): ExchangeType = ExchangeType.SHARDING
    override fun autoDelete(): Boolean = true

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

    private suspend fun getPlayerTimeTokensForTravel(
        gameSessionId: GameSessionId,
        travellerId: PlayerId,
        travelName: TravelName
    ): TimeTokensCoopInfo {
        val (travelCost, actualTimeTokenAmount) = Transactor.dbQuery {
            val travelCost = TravelDao.getTravelTimeCost(gameSessionId, travelName).getOrElse { 0.nonNeg }
            val actualTimeTokenAmount =
                PlayerTimeTokenDao.getPlayerTokens(gameSessionId, travellerId)
                    .map { it.values.filter { timeState -> timeState.isReady() } }
                    .map { it.size.nonNeg }
                    .getOrElse { 0.nonNeg }
            (travelCost to actualTimeTokenAmount)
        }

        return TimeTokensCoopInfo(AmountDiff(actualTimeTokenAmount to travelCost))
    }

    private suspend fun getPlayersEquipmentForCoop(
        gameSessionId: GameSessionId,
        players: NonEmptyMap<PlayerId, NonEmptyMap<GameResourceName, NonNegInt>>,
        travellerId: PlayerId,
        travelName: TravelName
    ): Option<NonEmptyMap<PlayerId, CoopPlayerEquipment>> = option {
        val equipments = Transactor.dbQuery {
            PlayerResourceDao.getUsersEquipments(gameSessionId, players.keys.toList())
        }

        val timeTokensCoopInfo = getPlayerTimeTokensForTravel(gameSessionId, travellerId, travelName)

        val coopEquipments = equipments.mapValues { (key, value) ->
            CoopPlayerEquipment.invoke(
                value.resources,
                players[key].toOption().bind(),
                timeTokensCoopInfo.some().filter { key == travellerId }
            )
        }

        coopEquipments.forEach { (playerId, coopEquipment) ->
            coopEquipment.validate().onLeft {
                logger.info("Not enough resources for player $playerId, $it")
            }
        }
        coopEquipments.toNonEmptyMapOrNone().bind()
    }

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: EquipmentInternalMessage
    ) {
        logger.info("[EQ-CHANGE] Player $senderId sent $message at $sentAt")
        val tokensUsedAction: ParZipFunction = {
            message.updatedTokens().onSome { updatedTokens ->
                updatedTokens.timeTokensUsed.map { tokensUsed ->
                    interactionMessageProducer.sendMessage(
                        gameSessionId,
                        PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                        TimeMessages.TimeSystemOutputMessage.PlayerTokensRefresh(senderId, tokensUsed)
                    )
                }
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
                    val senderNegotiatedResources = coopState.negotiatedBid.map { it.second.resources }.bind()
                    val secondPlayerNegotiatedResources =
                        secondPlayerState.negotiatedBid.map { it.second.resources }.bind()

                    val travellerId = coopState.traveller()
                    val travelName = coopState.travelName

                    val coopEquipments = getPlayersEquipmentForCoop(
                        gameSessionId,
                        nonEmptyMapOf(
                            senderId to senderNegotiatedResources,
                            secondPlayerId to secondPlayerNegotiatedResources
                        ),
                        travellerId,
                        travelName
                    ).bind()

                    if (coopEquipments.all { (_, value) -> value.validate().isRight() }) {
                        logger.info("Player equipment valid for travel 8)")
                        coopInternalMessageProducer.sendMessage(
                            gameSessionId,
                            senderId,
                            CoopInternalMessages.UserInputMessage.ResourcesGatheredUser(
                                secondPlayerId.some(),
                                coopEquipments
                            )
                        )
                    } else {
                        logger.info("Player equipment not valid for travel ;)")
                        coopInternalMessageProducer.sendMessage(
                            gameSessionId,
                            senderId,
                            CoopInternalMessages.UserInputMessage.ResourcesUnGatheredUser(
                                secondPlayerId,
                                coopEquipments
                            )
                        )
                    }
                } else if (coopState is CoopStates.GatheringResources || coopState is CoopStates.WaitingForCompany) {
                    val travelName = coopState.travelName().bind()
                    val travelCosts =
                        Transactor.dbQuery {
                            TravelDao.getTravelCostsByName(
                                gameSessionId,
                                travelName
                            )
                        }.bind()
                    val senderCoopEquipment = getPlayersEquipmentForCoop(
                        gameSessionId,
                        nonEmptyMapOf(senderId to travelCosts),
                        senderId,
                        travelName
                    ).flatMap { it[senderId].toOption() }.bind()

                    senderCoopEquipment.validate().onLeft {
                        logger.info("Player equipment not valid for travel ;)")
                        coopInternalMessageProducer.sendMessage(
                            gameSessionId,
                            senderId,
                            CoopInternalMessages.UserInputMessage.ResourcesUnGatheredSingleUser(
                                senderCoopEquipment
                            )
                        )
                    }.onRight {
                        logger.info("Player equipment valid for travel 8)")
                        coopInternalMessageProducer.sendMessage(
                            gameSessionId,
                            senderId,
                            CoopInternalMessages.UserInputMessage.ResourcesGatheredUser(
                                none(),
                                nonEmptyMapOf(
                                    senderId to senderCoopEquipment
                                )
                            )
                        )
                    }
                }
            }.onNone { logger.info("Error handling equipment change detected") }
        }

        when (message) {
            is EquipmentInternalMessage.TimeTokenRegenerated -> coroutineScope(coopEquipmentAction)
            is EquipmentInternalMessage.CheckEquipmentsForCoop -> coroutineScope(coopEquipmentAction)
            is EquipmentInternalMessage.EquipmentChangeAfterCoop -> parZip(
                equipmentChangeAction,
                tokensUsedAction
            ) { _, _ -> }

            is EquipmentInternalMessage.EquipmentChangeWithTokens -> parZip(
                equipmentChangeAction,
                coopEquipmentAction,
                tokensUsedAction
            ) { _, _, _ -> }
        }
    }
}
