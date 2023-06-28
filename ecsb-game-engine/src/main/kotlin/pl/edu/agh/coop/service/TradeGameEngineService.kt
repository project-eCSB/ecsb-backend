package pl.edu.agh.coop.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.option
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.coop.domain.TradeEquipments
import pl.edu.agh.coop.redis.TradeStatesDataConnector
import pl.edu.agh.domain.*
import pl.edu.agh.game.dao.GameSessionUserClassesDao
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionConsumerCallback
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.domain.TradeStates
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.susTupled2
import pl.edu.agh.utils.tupled2
import java.time.LocalDateTime

class TradeGameEngineService(
    private val tradeStatesDataConnector: TradeStatesDataConnector,
    private val redisInteractionStatusConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionStatus>,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>
) : InteractionConsumerCallback<TradeInternalMessages.UserInputMessage> {
    private val logger by LoggerDelegate()

    override val tSerializer: KSerializer<TradeInternalMessages.UserInputMessage> =
        TradeInternalMessages.UserInputMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "trade-in-$hostTag"
    override fun exchangeName(): String = InteractionProducer.TRADE_MESSAGES_EXCHANGE

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
        message: TradeInternalMessages.UserInputMessage
    ) {
        logger.info("Got message from $gameSessionId $senderId sent at $sentAt ($message)")
        when (message) {
            is TradeInternalMessages.UserInputMessage.FindTrade -> proposeTradeWithBid(
                gameSessionId,
                senderId,
                message.offer
            )

            is TradeInternalMessages.UserInputMessage.FindTradeAck -> acceptTradeWithBid(
                gameSessionId,
                senderId,
                message.offer,
                message.bidSenderId
            )

            is TradeInternalMessages.UserInputMessage.ProposeTrade -> forwardTradeProposal(
                gameSessionId,
                senderId,
                message
            )

            is TradeInternalMessages.UserInputMessage.ProposeTradeAck -> acceptNormalTrade(
                gameSessionId,
                senderId,
                message
            )

            is TradeInternalMessages.UserInputMessage.TradeBidMsg -> forwardTradeBid(
                gameSessionId,
                senderId,
                message
            )

            is TradeInternalMessages.UserInputMessage.TradeBidAck -> finishTrade(
                gameSessionId,
                senderId,
                message
            )

            TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> cancelTrade(gameSessionId, senderId)
        }.onLeft { logger.error("Can't do this operation now because $it") }
    }

    private suspend fun validateMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        message: TradeInternalMessages
    ): Either<String, Pair<PlayerId, TradeStates>> =
        tradeStatesDataConnector
            .getPlayerState(gameSessionId, playerId)
            .let { it.parseCommand(message).map { tradeStates -> playerId to tradeStates } }

    private suspend fun proposeTradeWithBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        tradeBid: TradeBid
    ): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateSetter = redisInteractionStatusConnector::changeData.partially1(gameSessionId)::susTupled2

        val newPlayerStatus =
            validationMethod(senderId to TradeInternalMessages.UserInputMessage.FindTrade(senderId, tradeBid)).bind()

        playerTradeStateSetter(newPlayerStatus)
        interactionStateSetter(senderId to InteractionStatus.BUSY)

        interactionSendingMessages(
            senderId to TradeMessages.TradeSystemInputMessage.SearchingForTrade(
                tradeBid,
                senderId
            )
        )
    }

    private suspend fun acceptTradeWithBid(
        gameSessionId: GameSessionId,
        currentPlayerId: PlayerId,
        tradeBid: TradeBid,
        proposalSenderId: PlayerId
    ): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateSetter = redisInteractionStatusConnector::changeData.partially1(gameSessionId)::susTupled2

        val playerTradeStates = listOf(
            currentPlayerId to TradeInternalMessages.UserInputMessage.FindTradeAck(tradeBid, proposalSenderId),
            proposalSenderId to TradeInternalMessages.SystemInputMessage.FindTradeAck(currentPlayerId, tradeBid)
        ).traverse { validationMethod(it) }.bind()
        playerTradeStates.forEach { playerTradeStateSetter(it) }

        val playerStates = listOf(
            currentPlayerId to InteractionStatus.BUSY,
            proposalSenderId to InteractionStatus.BUSY
        )
        playerStates.forEach { interactionStateSetter(it) }

        listOf(
            proposalSenderId to ChatMessageADT.SystemInputMessage.NotificationTradeStart(proposalSenderId),
            currentPlayerId to ChatMessageADT.SystemInputMessage.NotificationTradeStart(currentPlayerId)
        ).forEach { interactionSendingMessages(it) }
    }

    private suspend fun cancelTrade(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> = either {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateDelete = redisInteractionStatusConnector::removeElement.partially1(gameSessionId)

        val maybeSecondPlayerId = tradeStatesDataConnector.getPlayerState(gameSessionId, senderId).secondPlayer()

        val playerStates = listOf(senderId.some(), maybeSecondPlayerId)
            .flattenOption()
            .map { it to TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage }
            .traverse { validationMethod(it) }.bind()

        playerStates.forEach { playerTradeStateSetter(it) }

        interactionStateDelete(senderId)
        interactionSendingMessages(senderId to TradeMessages.TradeSystemInputMessage.CancelTradeAtAnyStage)
        interactionSendingMessages(senderId to ChatMessageADT.SystemInputMessage.NotificationTradeEnd(senderId))

        maybeSecondPlayerId
            .onSome {
                interactionStateDelete(senderId)
                interactionSendingMessages(it to TradeMessages.TradeSystemInputMessage.CancelTradeAtAnyStage)
                interactionSendingMessages(it to ChatMessageADT.SystemInputMessage.NotificationTradeEnd(it))
            }
    }

    private suspend fun forwardTradeProposal(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.ProposeTrade
    ): Either<String, Unit> = either {
        val receiverId = message.proposalReceiverId
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2

        val senderStatus = validationMethod(senderId to message).bind()
        val receiverStatusBefore = tradeStatesDataConnector.getPlayerState(gameSessionId, receiverId)
        validationMethod(receiverId to TradeInternalMessages.SystemInputMessage.ProposeTrade(senderId)).bind()
        if (receiverStatusBefore.busy()) {
            interactionSendingMessages(
                receiverId to ChatMessageADT.SystemInputMessage.UserBusyMessage(
                    "I'm busy, still waiting for assets from WH",
                    senderId
                )
            )
        } else {
            playerTradeStateSetter(senderStatus)
            interactionSendingMessages(senderId to TradeMessages.TradeUserInputMessage.ProposeTradeMessage(message.proposalReceiverId))
        }
    }

    private suspend fun acceptNormalTrade(
        gameSessionId: GameSessionId,
        proposalReceiverId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.ProposeTradeAck
    ): Either<String, Unit> = either {
        val proposalSenderId = message.proposalSenderId
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateSetter = redisInteractionStatusConnector::changeData.partially1(gameSessionId)::susTupled2

        val receiverStatus = validationMethod(proposalReceiverId to message).bind()
        val senderStatusBefore = tradeStatesDataConnector.getPlayerState(gameSessionId, proposalReceiverId)
        val senderStatusAfter = validationMethod(
            proposalSenderId to TradeInternalMessages.SystemInputMessage.ProposeTradeAck(proposalReceiverId)
        ).bind()

        if (senderStatusBefore.busy()) {
            interactionSendingMessages(
                proposalSenderId to ChatMessageADT.SystemInputMessage.UserBusyMessage(
                    "Accepted to late :(",
                    proposalReceiverId
                )
            )
        } else {
            option {
                val equipments = getPlayersEquipmentsForTrade(
                    gameSessionId,
                    proposalSenderId,
                    proposalReceiverId
                ).map(::TradeEquipments::tupled2).bind()

                playerTradeStateSetter(receiverStatus)
                playerTradeStateSetter(senderStatusAfter)

                interactionStateSetter(
                    proposalReceiverId to InteractionStatus.BUSY
                )
                interactionStateSetter(
                    proposalSenderId to InteractionStatus.BUSY
                )

                listOf(
                    proposalReceiverId to TradeMessages.TradeSystemInputMessage.TradeAckMessage(
                        true,
                        equipments.receiverEquipment,
                        proposalSenderId
                    ),
                    proposalSenderId to TradeMessages.TradeSystemInputMessage.TradeAckMessage(
                        false,
                        equipments.senderEquipment,
                        proposalReceiverId
                    ),
                    proposalReceiverId to ChatMessageADT.SystemInputMessage.NotificationTradeStart(proposalReceiverId),
                    proposalSenderId to ChatMessageADT.SystemInputMessage.NotificationTradeStart(proposalReceiverId)
                ).forEach { interactionSendingMessages(it) }
            }
        }
    }

    private suspend fun forwardTradeBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.TradeBidMsg
    ): Either<String, Unit> = either {
        val (tradeBid, receiverId) = message
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2

        Transactor.dbQuery {
            validateResources(gameSessionId, tradeBid).bind()
        }

        val newStates = listOf(
            senderId to message,
            receiverId to TradeInternalMessages.SystemInputMessage.TradeBidMsg(
                senderId,
                tradeBid
            )
        ).traverse { validationMethod(it) }.bind()

        newStates.forEach { playerTradeStateSetter(it) }

        interactionSendingMessages(
            senderId to TradeMessages.TradeUserInputMessage.TradeBidMessage(
                tradeBid,
                receiverId
            )
        )
    }

    private suspend fun finishTrade(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.TradeBidAck
    ): Either<String, Unit> = either {
        val (finalBid, receiverId) = message
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2
        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2
        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
        val interactionStateDelete = redisInteractionStatusConnector::removeElement.partially1(gameSessionId)

        val newStates = listOf(
            senderId to message,
            receiverId to TradeInternalMessages.SystemInputMessage.TradeBidAck(
                senderId,
                finalBid
            )
        ).traverse { validationMethod(it) }.bind()

        Transactor.dbQuery {
            validateResources(gameSessionId, finalBid).bind()
            logger.info("Finishing trade for $senderId and $receiverId")
            logger.info("Updating equipment of players $senderId, $receiverId in game session $gameSessionId")
            PlayerResourceDao.updateResources(gameSessionId, senderId, finalBid.senderRequest, finalBid.senderOffer)
            PlayerResourceDao.updateResources(gameSessionId, receiverId, finalBid.senderOffer, finalBid.senderRequest)
        }

        newStates.forEach { playerTradeStateSetter(it) }

        interactionStateDelete(senderId)
        interactionStateDelete(receiverId)

        listOf(
            senderId to ChatMessageADT.SystemInputMessage.NotificationTradeEnd(senderId),
            receiverId to ChatMessageADT.SystemInputMessage.NotificationTradeEnd(receiverId),
            PlayerIdConst.ECSB_CHAT_PLAYER_ID to TradeMessages.TradeSystemInputMessage.TradeFinishMessage(senderId),
            PlayerIdConst.ECSB_CHAT_PLAYER_ID to TradeMessages.TradeSystemInputMessage.TradeFinishMessage(receiverId)
        ).forEach { interactionSendingMessages(it) }
    }

    private fun validateResources(
        gameSessionId: GameSessionId,
        tradeBid: TradeBid
    ): Either<String, Unit> = either {
        val gameResourcesCount = GameSessionUserClassesDao.getClasses(gameSessionId).map { map -> map.size }
            .toEither { "Error getting resoruces from game session $gameSessionId" }.bind()
        val bidOffer = tradeBid.senderOffer.resources
        val bidRequest = tradeBid.senderRequest.resources
        if (bidOffer.keys.intersect(bidRequest.keys).size != bidOffer.size || bidOffer.size != gameResourcesCount) {
            raise("Wrong resource count")
        }
    }

    private suspend fun getPlayersEquipmentsForTrade(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> =
        Transactor.dbQuery {
            logger.info("Fetching equipments of players $player1 and $player2 for trade in game session $gameSessionId")
            PlayerResourceDao.getUsersSharedEquipments(gameSessionId, player1, player2)
        }
}
