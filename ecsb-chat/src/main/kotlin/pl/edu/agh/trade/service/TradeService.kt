package pl.edu.agh.trade.service

import arrow.core.partially1
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime

/**
 * Resends received external TradeMessages as TradeInternalMessages to game engine module & analytics module
 */
class TradeService(private val interactionProducer: InteractionProducer<TradeInternalMessages.UserInputMessage>) {

    private val logger by LoggerDelegate()

    suspend fun handleIncomingTradeMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        tradeMessage: TradeMessages.TradeUserInputMessage
    ) {
        val sentAt = LocalDateTime.now()
        logger.info("Player $playerId sent message in game $gameSessionId with content $tradeMessage at $sentAt")
        val sender = interactionProducer::sendMessage.partially1(gameSessionId).partially1(playerId)
        when (tradeMessage) {
            is TradeMessages.TradeUserInputMessage.AdvertiseTradeMessage -> sender(
                TradeInternalMessages.UserInputMessage.AdvertiseTradeUser(playerId)
            )

            is TradeMessages.TradeUserInputMessage.AdvertiseTradeAckMessage -> sender(
                TradeInternalMessages.UserInputMessage.AdvertiseTradeAckUser(tradeMessage.proposalSenderId)
            )

            is TradeMessages.TradeUserInputMessage.ProposeTradeMessage -> sender(
                TradeInternalMessages.UserInputMessage.ProposeTradeUser(
                    playerId,
                    tradeMessage.proposalReceiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.ProposeTradeAckMessage -> sender(
                TradeInternalMessages.UserInputMessage.ProposeTradeAckUser(
                    tradeMessage.proposalSenderId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeBidMessage -> sender(
                TradeInternalMessages.UserInputMessage.TradeBidUser(
                    tradeMessage.tradeBid,
                    tradeMessage.receiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeBidAckMessage -> sender(
                TradeInternalMessages.UserInputMessage.TradeBidAckUser(
                    tradeMessage.finalBid,
                    tradeMessage.receiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeMinorChange -> sender(
                TradeInternalMessages.UserInputMessage.TradeMinorChange(
                    tradeMessage.tradeBid,
                    tradeMessage.receiverId
                )
            )

            TradeMessages.TradeUserInputMessage.CancelTradeAtAnyStage -> sender(
                TradeInternalMessages.UserInputMessage.CancelTradeUser
            )
        }
    }

    suspend fun cancelAllPlayerTrades(gameSessionId: GameSessionId, playerId: PlayerId) =
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            TradeInternalMessages.UserInputMessage.CancelTradeUser
        )
}
