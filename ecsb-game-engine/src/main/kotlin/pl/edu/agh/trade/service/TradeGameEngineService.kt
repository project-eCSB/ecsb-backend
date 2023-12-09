package pl.edu.agh.trade.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.trade.domain.AdvertiseDto
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.domain.TradeStates
import pl.edu.agh.trade.redis.AdvertisementStateDataConnector
import pl.edu.agh.trade.redis.TradeStatesDataConnector
import pl.edu.agh.utils.*
import java.time.LocalDateTime

class TradeGameEngineService(
    private val tradeStatesDataConnector: TradeStatesDataConnector,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val equipmentTradeService: EquipmentTradeService,
    private val tradeAdvertisementDataConnector: AdvertisementStateDataConnector,
    private val interactionDataConnector: InteractionDataService = InteractionDataService.instance
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
    override fun exchangeType(): ExchangeType = ExchangeType.SHARDING
    override fun autoDelete(): Boolean = true

    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: TradeInternalMessages.UserInputMessage
    ) {
        logger.info("Got message from $gameSessionId $senderId sent at $sentAt ($message)")
        when (message) {
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

            is TradeInternalMessages.UserInputMessage.AdvertiseBuy -> advertiseBuy(
                gameSessionId,
                senderId,
                message.gameResourceName
            )

            is TradeInternalMessages.UserInputMessage.AdvertiseSell -> advertiseSell(
                gameSessionId,
                senderId,
                message.gameResourceName
            )

            TradeInternalMessages.UserInputMessage.CancelTradeUser -> cancelTrade(gameSessionId, senderId)
            TradeInternalMessages.SystemInputMessage.SyncAdvertisement -> syncAdvertisement(gameSessionId, senderId)
            TradeInternalMessages.UserInputMessage.StopAdvertisement -> stopAdvertising(gameSessionId, senderId)
            is TradeInternalMessages.UserInputMessage.TradeMinorChange -> Unit.right()
        }.onLeft {
            logger.warn("WARNING: $it, GAME: ${GameSessionId.toName(gameSessionId)}, SENDER: ${senderId.value}, SENT AT: $sentAt, SOURCE: $message")
            interactionProducer.sendMessage(
                gameSessionId,
                PlayerIdConst.ECSB_TRADE_PLAYER_ID,
                ChatMessageADT.SystemOutputMessage.UserWarningMessage(it, senderId)
            )
        }
    }

    private suspend fun advertise(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        action: (AdvertiseDto) -> AdvertiseDto
    ) {
        val newState =
            action(tradeAdvertisementDataConnector.getPlayerState(gameSessionId, senderId))
        tradeAdvertisementDataConnector.setPlayerState(gameSessionId, senderId, newState)
    }

    private suspend fun advertiseBuy(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        gameResourceName: GameResourceName
    ): Either<String, Unit> = either {
        advertise(gameSessionId, senderId) { it.copy(buy = gameResourceName.some()) }

        interactionProducer.sendMessage(
            gameSessionId,
            senderId,
            TradeMessages.TradeSystemOutputMessage.AdvertiseBuy(gameResourceName)
        )
    }

    private suspend fun advertiseSell(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        gameResourceName: GameResourceName
    ): Either<String, Unit> = either {
        advertise(gameSessionId, senderId) { it.copy(sell = gameResourceName.some()) }

        interactionProducer.sendMessage(
            gameSessionId,
            senderId,
            TradeMessages.TradeSystemOutputMessage.AdvertiseSell(gameResourceName)
        )
    }

    private suspend fun stopAdvertising(
        gameSessionId: GameSessionId,
        senderId: PlayerId
    ): Either<String, Unit> = either {
        advertise(gameSessionId, senderId) { AdvertiseDto(none(), none()) }
        listOf(
            TradeMessages.TradeSystemOutputMessage.AdvertiseBuy(GameResourceName("")),
            TradeMessages.TradeSystemOutputMessage.AdvertiseSell(GameResourceName(""))
        ).forEach { interactionProducer.sendMessage(gameSessionId, senderId, it) }
    }

    private suspend fun syncAdvertisement(
        gameSessionId: GameSessionId,
        senderId: PlayerId
    ): Either<String, Unit> = either {
        val states = tradeAdvertisementDataConnector.getPlayerStates(gameSessionId)
            .filterNot { (key, _) -> key == senderId }

        interactionProducer.sendMessage(
            gameSessionId,
            PlayerIdConst.ECSB_CHAT_PLAYER_ID,
            TradeMessages.TradeSystemOutputMessage.TradeSyncMessage(senderId, states.toNonEmptyMapOrNone())
        )
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
                    .map { tradeStates -> playerId to tradeStates }
            }

    private suspend fun cancelTrade(gameSessionId: GameSessionId, senderId: PlayerId): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val interactionStateDelete = interactionDataConnector::removeInteractionData.partially1(gameSessionId)
        val maybeSecondPlayerId = tradeStatesDataConnector.getPlayerState(gameSessionId, senderId).secondPlayer()

        listOf(senderId.some(), maybeSecondPlayerId)
            .flattenOption()
            .map { it to TradeInternalMessages.SystemInputMessage.CancelTradeSystem }
            .traverse { methods.validationMethod(it) }.bind()
            .map { methods.playerTradeStateSetter(it) }

        interactionStateDelete(senderId)
        listOf(
            TradeMessages.TradeSystemOutputMessage.CancelTradeAtAnyStage(senderId),
            TradeMessages.TradeSystemOutputMessage.NotificationTradeEnd
        ).forEach { methods.interactionSendingMessages(senderId to it) }

        maybeSecondPlayerId
            .onSome {
                interactionStateDelete(it)
                listOf(
                    senderId to TradeMessages.TradeSystemOutputMessage.CancelTradeAtAnyStage(it),
                    it to TradeMessages.TradeSystemOutputMessage.NotificationTradeEnd
                ).forEach { pair -> methods.interactionSendingMessages(pair) }
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
            senderId to TradeMessages.TradeSystemOutputMessage.ProposeTradeMessage(message.proposalReceiverId)
        )
    }

    private suspend fun acceptNormalTrade(
        gameSessionId: GameSessionId,
        proposalReceiverId: PlayerId,
        message: TradeInternalMessages.UserInputMessage.ProposeTradeAckUser
    ): Either<String, Unit> = either {
        val methods = TradePAMethods(gameSessionId)
        val proposalSenderId = message.proposalSenderId
        val newStatuses = listOf(
            proposalReceiverId to message,
            proposalSenderId to TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem(proposalReceiverId)
        ).traverse { methods.validationMethod(it) }.bind()
        logger.info("Fetching equipments of players $proposalSenderId and $proposalReceiverId for trade in game session $gameSessionId")

        ensure(
            interactionDataConnector.setInteractionDataForPlayers(
                gameSessionId,
                nonEmptyMapOf(
                    proposalReceiverId to InteractionStatus.TRADE_BUSY,
                    proposalSenderId to InteractionStatus.TRADE_BUSY
                )
            )
        ) {
            logger.error("$proposalReceiverId or $proposalSenderId is busy, so they could not start trade")
            "${proposalReceiverId.value} or ${proposalSenderId.value} is busy, so they could not start trade"
        }

        newStatuses.forEach { methods.playerTradeStateSetter(it) }

        listOf(
            proposalReceiverId to TradeMessages.TradeSystemOutputMessage.TradeAckMessage(true, proposalSenderId),
            proposalSenderId to TradeMessages.TradeSystemOutputMessage.TradeAckMessage(false, proposalReceiverId),
            proposalReceiverId to TradeMessages.TradeSystemOutputMessage.NotificationTradeStart,
            proposalSenderId to TradeMessages.TradeSystemOutputMessage.NotificationTradeStart
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

        listOf(
            senderId to message,
            receiverId to TradeInternalMessages.SystemInputMessage.TradeBidSystem(senderId)
        ).traverse { methods.validationMethod(it) }
            .bind()
            .map { methods.playerTradeStateSetter(it) }

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

        val newStates = listOf(
            senderId to message,
            receiverId to TradeInternalMessages.SystemInputMessage.TradeBidAckSystem(senderId)
        ).traverse { methods.validationMethod(it) }.bind()

        logger.info("Finishing trade for $senderId and $receiverId")
        logger.info("Updating equipment of players $senderId, $receiverId in game session $gameSessionId")
        equipmentTradeService.finishTrade(gameSessionId, finalBid, senderId, receiverId).bind()

        newStates.forEach { methods.playerTradeStateSetter(it) }
        listOf(senderId, receiverId).forEach { interactionDataConnector.removeInteractionData(gameSessionId, it) }

        listOf(
            senderId to TradeMessages.TradeSystemOutputMessage.NotificationTradeEnd,
            receiverId to TradeMessages.TradeSystemOutputMessage.NotificationTradeEnd,
            PlayerIdConst.ECSB_TRADE_PLAYER_ID to TradeMessages.TradeSystemOutputMessage.TradeFinishMessage(senderId),
            PlayerIdConst.ECSB_TRADE_PLAYER_ID to TradeMessages.TradeSystemOutputMessage.TradeFinishMessage(receiverId)
        ).forEach { methods.interactionSendingMessages(it) }
    }
}
