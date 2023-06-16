package pl.edu.agh.trade.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.option
import pl.edu.agh.chat.domain.InteractionDto
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.chat.domain.TradeBid
import pl.edu.agh.chat.redis.InteractionDataConnector
import pl.edu.agh.chat.route.MessageValidationError
import pl.edu.agh.chat.service.InteractionProducer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus.*
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.trade.domain.TradeEquipments
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.tupled2

interface TradeService {
    suspend fun tradeRequest(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Unit>

    suspend fun tradeAck(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Option<TradeEquipments>>

    suspend fun tradeBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Unit>

    suspend fun tradeCancel(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Unit>

    suspend fun tradeFinalize(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId,
        finalBid: TradeBid
    ): Either<MessageValidationError, Unit>

    suspend fun cancelPlayerTrades(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<PlayerId>
}

class TradeServiceImpl(
    private val interactionDataConnector: InteractionDataConnector,
    private val interactionProducer: InteractionProducer
) : TradeService {
    private val logger by LoggerDelegate()

    private suspend fun getPlayersEquipmentsForTrade(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> =
        Transactor.dbQuery {
            logger.info("Fetching equipments of players $player1 and $player2 for trade in game session $gameSessionId")
            PlayerResourceDao.getUsersEquipments(gameSessionId, player1, player2)
        }

    private suspend fun updatePlayerEquipment(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        equipmentChanges: PlayerEquipment
    ) =
        Transactor.dbQuery {
            logger.info("Updating equipment of player $playerId in game session $gameSessionId")
            PlayerResourceDao.updateResources(gameSessionId, playerId, equipmentChanges)
        }

    private val playerBusyCheck: (InteractionDto) -> Boolean =
        {
            when (it.status) {
                TRADE_OFFER -> false
                COMPANY_OFFER -> false
                TRADE_IN_PROGRESS -> true
                IN_WORKSHOP -> true
                PRODUCTION -> true
                TRAVEL -> true
                COMPANY_IN_PROGRESS -> true
            }
        }

    private val playerNotInTradeCheck: (InteractionDto) -> Boolean =
        { it.status != TRADE_IN_PROGRESS }

    private suspend fun checkPlayerStatus(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        check: (InteractionDto) -> Boolean
    ): Boolean =
        interactionDataConnector.findOne(gameSessionId, playerId).isSome { check(it) }

    private suspend fun validateMessage(
        gameSessionId: GameSessionId,
        receiverId: PlayerId,
        senderId: PlayerId,
        check: (InteractionDto) -> Boolean
    ): Either<MessageValidationError, Unit> = either {
        if (senderId == receiverId) {
            raise(MessageValidationError.SamePlayer)
        }
        if (checkPlayerStatus(gameSessionId, receiverId, check)) {
            raise(MessageValidationError.CheckFailed)
        }
    }

    override suspend fun tradeRequest(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Unit> =
        validateMessage(gameSessionId, receiverId, senderId, playerBusyCheck)

    override suspend fun tradeAck(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Option<TradeEquipments>> =
        validateMessage(gameSessionId, receiverId, senderId, playerBusyCheck)
            .map {
                option {
                    val equipments = getPlayersEquipmentsForTrade(
                        gameSessionId,
                        senderId,
                        receiverId
                    ).map(::TradeEquipments::tupled2).bind()

                    interactionDataConnector.setInteractionData(
                        gameSessionId,
                        senderId,
                        InteractionDto(TRADE_IN_PROGRESS, receiverId)
                    )
                    interactionDataConnector.setInteractionData(
                        gameSessionId,
                        receiverId,
                        InteractionDto(TRADE_IN_PROGRESS, senderId)
                    )

                    interactionProducer.sendMessage(
                        gameSessionId,
                        senderId,
                        MessageADT.SystemInputMessage.TradeStart(senderId)
                    )
                    interactionProducer.sendMessage(
                        gameSessionId,
                        senderId,
                        MessageADT.SystemInputMessage.TradeStart(receiverId)
                    )

                    equipments
                }
            }

    override suspend fun tradeBid(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Unit> =
        validateMessage(gameSessionId, receiverId, senderId, playerNotInTradeCheck)

    override suspend fun tradeCancel(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<MessageValidationError, Unit> =
        validateMessage(gameSessionId, receiverId, senderId, playerNotInTradeCheck)
            .map { _ ->
                logger.info("Canceling trade for $senderId and $receiverId")

                interactionProducer.sendMessage(
                    gameSessionId,
                    senderId,
                    MessageADT.SystemInputMessage.TradeEnd(receiverId)
                )
                interactionDataConnector.removeInteractionData(gameSessionId, receiverId)

                interactionProducer.sendMessage(
                    gameSessionId,
                    senderId,
                    MessageADT.SystemInputMessage.TradeEnd(senderId)
                )
                interactionDataConnector.removeInteractionData(gameSessionId, senderId)
            }

    override suspend fun tradeFinalize(
        gameSessionId: GameSessionId, senderId: PlayerId, receiverId: PlayerId, finalBid: TradeBid
    ): Either<MessageValidationError, Unit> = either {
        validateMessage(gameSessionId, receiverId, senderId, playerNotInTradeCheck).bind()


        listOf(finalBid.senderOffer, finalBid.senderRequest)
            .traverse(PlayerEquipment::validatePositive)
            .fold(
                ifLeft = { error ->
                    logger.error(error)
                    MessageValidationError.CheckFailed.left()
                },
                ifRight = {
                    it.right()
                }
            ).bind()

        logger.info("Finishing trade for $senderId and $receiverId")
        val equipmentChanges = finalBid.senderRequest - finalBid.senderOffer
        updatePlayerEquipment(
            gameSessionId = gameSessionId,
            playerId = senderId,
            equipmentChanges = equipmentChanges
        )
        updatePlayerEquipment(
            gameSessionId = gameSessionId,
            playerId = receiverId,
            equipmentChanges = PlayerEquipment.getInverse(equipmentChanges)
        )

        interactionProducer.sendMessage(
            gameSessionId,
            senderId,
            MessageADT.SystemInputMessage.TradeEnd(receiverId)
        )
        interactionDataConnector.removeInteractionData(gameSessionId, receiverId)

        interactionProducer.sendMessage(
            gameSessionId,
            senderId,
            MessageADT.SystemInputMessage.TradeEnd(senderId)
        )
        interactionDataConnector.removeInteractionData(gameSessionId, senderId)
    }

    override suspend fun cancelPlayerTrades(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerId> =
        interactionDataConnector.findOne(gameSessionId, playerId).flatMap { interactionDto ->
            val playerInTrade = playerNotInTradeCheck.andThen { !it }
            if (playerInTrade(interactionDto)) {
                interactionDto.otherPlayer.some()
            } else {
                none()
            }
        }
}
