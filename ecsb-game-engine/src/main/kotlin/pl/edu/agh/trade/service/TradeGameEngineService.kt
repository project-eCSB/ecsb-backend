package pl.edu.agh.trade.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradeEquipments
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.domain.TradeStates
import pl.edu.agh.trade.redis.TradeStatesDataConnector
import pl.edu.agh.utils.*
import java.time.LocalDateTime

class TradeGameEngineService(
    private val tradeStatesDataConnector: TradeStatesDataConnector,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val interactionDataConnector: InteractionDataService = InteractionDataService.instance,
    private val equipmentTradeService: EquipmentTradeService = EquipmentTradeService.instance
) : InteractionConsumer<TradeInternalMessages.UserInputMessage> {

    private inner class TradePAMethods(gameSessionId: GameSessionId) {
        val validationMethod = ::validateMessage.partially1(gameSessionId)::susTupled2

        val interactionSendingMessages = interactionProducer::sendMessage.partially1(gameSessionId)::susTupled2

        val playerTradeStateSetter = tradeStatesDataConnector::setPlayerState.partially1(gameSessionId)::susTupled2
    }

    private val logger by LoggerDelegate()

    override val tSerializer: KSerializer<TradeInternalMessages.UserInputMessage> =
        TradeInternalMessages.UserInputMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "trade-in-$hostTag"
    override fun exchangeName(): String = InteractionProducer.TRADE_MESSAGES_EXCHANGE

    override fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), ExchangeType.SHARDING.value)
        channel.queueDeclare(queueName, true, false, true, mapOf())
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
            is TradeInternalMessages.UserInputMessage.FindTradeUser -> proposeTradeWithBid(
                gameSessionId,
                senderId,
                message.offer
            )

            is TradeInternalMessages.UserInputMessage.FindTradeAckUser -> acceptTradeWithBid(
                gameSessionId,
                senderId,
                message.offer,
                message.bidSenderId
            )

            is TradeInternalMessages.UserInputMessage.ProposeTradeUser -> forwardTradeProposal(
                gameSessionId,
                senderId,
                message
            )

            is TradeInternalMessages.UserInputMessage.ProposeTradeAckUser -> acceptNormalTrade(
                gameSessionId,
                senderId,
                message
            )

            is TradeInternalMessages.UserInputMessage.TradeBidUser -> forwardTradeBid(
                gameSessionId,
                senderId,
                message
            )

            is TradeInternalMessages.UserInputMessage.TradeBidAckUser -> finishTrade(
                gameSessionId,
                senderId,
                message
            )

            TradeInternalMessages.UserInputMessage.CancelTradeUser -> cancelTrade(gameSessionId, senderId)
            is TradeInternalMessages.UserInputMessage.TradeMinorChange -> Unit.right()
        }.onLeft {
            logger.warn("WARNING: $it, GAME: ${gameSessionId.value}, SENDER: ${senderId.value}, SENT AT: $sentAt, SOURCE: $message")
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserBusyMessage(it, senderId)
            )
        }
    }

    private suspend fun validateMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        message: TradeInternalMessages
    ): Either<String, Pair<PlayerId, TradeStates>> =
        tradeStatesDataConnector
            .getPlayerState(gameSessionId, playerId)
            .let {
                it.parseCommand(message).mapLeft { it(playerId) }
                    .map { tradeStates: TradeStates -> playerId to tradeStates }
            }

    private suspend fun proposeTradeWithBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        tradeBid: TradeBid
    ): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val newPlayerStatus =
            methods.validationMethod(
                senderId to TradeInternalMessages.UserInputMessage.FindTradeUser(
                    senderId,
                    tradeBid
                )
            ).bind()

        ensure(
            interactionDataConnector.setInteractionData(
                gameSessionId,
                senderId,
                InteractionStatus.TRADE_BUSY
            )
        ) { "You are busy mate" }
        methods.playerTradeStateSetter(newPlayerStatus)

        methods.interactionSendingMessages(
            senderId to TradeMessages.TradeSystemOutputMessage.SearchingForTrade(
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
        val methods = TradePAMethods(gameSessionId)
        val playerTradeStates = listOf(
            currentPlayerId to TradeInternalMessages.UserInputMessage.FindTradeAckUser(tradeBid, proposalSenderId),
            proposalSenderId to TradeInternalMessages.SystemInputMessage.FindTradeAckSystem(currentPlayerId, tradeBid)
        ).traverse { methods.validationMethod(it) }.bind()

        val playerStates = nonEmptyMapOf(
            currentPlayerId to InteractionStatus.TRADE_BUSY,
            proposalSenderId to InteractionStatus.TRADE_BUSY
        )
        ensure(
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                playerStates
            )
        ) { "One of you is busy :/" }

        playerTradeStates.forEach { methods.playerTradeStateSetter(it) }

        listOf(
            proposalSenderId to ChatMessageADT.SystemOutputMessage.NotificationTradeStart(proposalSenderId),
            currentPlayerId to ChatMessageADT.SystemOutputMessage.NotificationTradeStart(currentPlayerId)
        ).forEach { methods.interactionSendingMessages(it) }
    }

    private suspend fun cancelTrade(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)
        val maybeSecondPlayerId = tradeStatesDataConnector.getPlayerState(gameSessionId, senderId).secondPlayer()

        val playerStates = listOf(senderId.some(), maybeSecondPlayerId)
            .flattenOption()
            .map { it to TradeInternalMessages.SystemInputMessage.CancelTradeSystem }
            .traverse { methods.validationMethod(it) }.bind()

        playerStates.forEach { methods.playerTradeStateSetter(it) }

        interactionStateDelete(senderId)
        methods.interactionSendingMessages(senderId to TradeMessages.TradeSystemOutputMessage.CancelTradeAtAnyStage)
        methods.interactionSendingMessages(
            senderId to ChatMessageADT.SystemOutputMessage.NotificationTradeEnd(
                senderId
            )
        )

        maybeSecondPlayerId
            .onSome {
                interactionStateDelete(senderId)
                methods.interactionSendingMessages(it to TradeMessages.TradeSystemOutputMessage.CancelTradeAtAnyStage)
                methods.interactionSendingMessages(
                    it to ChatMessageADT.SystemOutputMessage.NotificationTradeEnd(
                        it
                    )
                )
            }
    }

    private suspend fun forwardTradeProposal(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.ProposeTradeUser
    ): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val receiverId = message.proposalReceiverId
        val senderStatus = methods.validationMethod(senderId to message).bind()
        methods.validationMethod(receiverId to TradeInternalMessages.SystemInputMessage.ProposeTradeSystem(senderId))
            .bind()
        methods.playerTradeStateSetter(senderStatus)
        methods.interactionSendingMessages(
            senderId to TradeMessages.TradeSystemOutputMessage.ProposeTradeMessage(
                message.proposalReceiverId
            )
        )
    }

    private suspend fun acceptNormalTrade(
        gameSessionId: GameSessionId,
        proposalReceiverId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.ProposeTradeAckUser
    ): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val proposalSenderId = message.proposalSenderId
        val receiverStatus = methods.validationMethod(proposalReceiverId to message).bind()
        val senderStatus = methods.validationMethod(
            proposalSenderId to TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem(proposalReceiverId)
        ).bind()

        logger.info("Fetching equipments of players $proposalSenderId and $proposalReceiverId for trade in game session $gameSessionId")

        val equipments = equipmentTradeService.getPlayersEquipmentsForTrade(
            gameSessionId,
            proposalSenderId,
            proposalReceiverId
        ).map(::TradeEquipments::tupled2).toEither { "Could not get some of your equipment" }.bind()

        val playerStatues = nonEmptyMapOf(
            proposalReceiverId to InteractionStatus.TRADE_BUSY,
            proposalSenderId to InteractionStatus.TRADE_BUSY
        )

        ensure(
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                playerStatues
            )
        ) { "Some of you is busy" }

        methods.playerTradeStateSetter(receiverStatus)
        methods.playerTradeStateSetter(senderStatus)

        listOf(
            proposalReceiverId to TradeMessages.TradeSystemOutputMessage.TradeAckMessage(
                true,
                equipments.receiverEquipment,
                proposalSenderId
            ),
            proposalSenderId to TradeMessages.TradeSystemOutputMessage.TradeAckMessage(
                false,
                equipments.senderEquipment,
                proposalReceiverId
            ),
            proposalReceiverId to ChatMessageADT.SystemOutputMessage.NotificationTradeStart(proposalReceiverId),
            proposalSenderId to ChatMessageADT.SystemOutputMessage.NotificationTradeStart(proposalSenderId)
        ).forEach { methods.interactionSendingMessages(it) }
    }

    private suspend fun forwardTradeBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.TradeBidUser
    ): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val (tradeBid, receiverId) = message

        equipmentTradeService.validateResources(gameSessionId, tradeBid).bind()

        val newStates = listOf(
            senderId to message,
            receiverId to TradeInternalMessages.SystemInputMessage.TradeBidSystem(
                senderId,
                tradeBid
            )
        ).traverse { methods.validationMethod(it) }.bind()

        newStates.forEach { methods.playerTradeStateSetter(it) }

        methods.interactionSendingMessages(
            senderId to TradeMessages.TradeSystemOutputMessage.TradeBidMessage(
                tradeBid,
                receiverId
            )
        )
    }

    private suspend fun finishTrade(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.TradeBidAckUser
    ): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val (finalBid, receiverId) = message
        val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)

        val newStates = listOf(
            senderId to message,
            receiverId to TradeInternalMessages.SystemInputMessage.TradeBidAckSystem(
                senderId,
                finalBid
            )
        ).traverse { methods.validationMethod(it) }.bind()

        logger.info("Finishing trade for $senderId and $receiverId")
        logger.info("Updating equipment of players $senderId, $receiverId in game session $gameSessionId")
        equipmentTradeService.finishTrade(gameSessionId, finalBid, senderId, receiverId).bind()

        newStates.forEach { methods.playerTradeStateSetter(it) }

        interactionStateDelete(senderId)
        interactionStateDelete(receiverId)

        listOf(
            senderId to ChatMessageADT.SystemOutputMessage.NotificationTradeEnd(senderId),
            receiverId to ChatMessageADT.SystemOutputMessage.NotificationTradeEnd(receiverId),
            PlayerIdConst.ECSB_CHAT_PLAYER_ID to TradeMessages.TradeSystemOutputMessage.TradeFinishMessage(senderId),
            PlayerIdConst.ECSB_CHAT_PLAYER_ID to TradeMessages.TradeSystemOutputMessage.TradeFinishMessage(receiverId)
        ).forEach { methods.interactionSendingMessages(it) }
    }
}
