package pl.edu.agh.trade.service

import arrow.core.partially1
import pl.edu.agh.chat.domain.ChatMessageADT
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
class TradeService(
    private val tradeInternalMessageProducer: InteractionProducer<TradeInternalMessages.UserInputMessage>,
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
) {

    private val logger by LoggerDelegate()

    suspend fun handleIncomingTradeMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        tradeMessage: TradeMessages.TradeUserInputMessage
    ) {
        val sentAt = LocalDateTime.now()
        logger.info("Player $playerId sent message in game $gameSessionId with content $tradeMessage at $sentAt")
        val tradeSender = tradeInternalMessageProducer::sendMessage.partially1(gameSessionId).partially1(playerId)
        when (tradeMessage) {
            is TradeMessages.TradeUserInputMessage.ProposeTradeMessage -> tradeSender(
                TradeInternalMessages.UserInputMessage.ProposeTradeUser(
                    playerId,
                    tradeMessage.proposalReceiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.ProposeTradeAckMessage -> tradeSender(
                TradeInternalMessages.UserInputMessage.ProposeTradeAckUser(
                    tradeMessage.proposalSenderId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeBidMessage -> tradeSender(
                TradeInternalMessages.UserInputMessage.TradeBidUser(
                    tradeMessage.tradeBid,
                    tradeMessage.receiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeBidAckMessage -> tradeSender(
                TradeInternalMessages.UserInputMessage.TradeBidAckUser(
                    tradeMessage.finalBid,
                    tradeMessage.receiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeMinorChange -> tradeSender(
                TradeInternalMessages.UserInputMessage.TradeMinorChange(
                    tradeMessage.tradeBid,
                    tradeMessage.receiverId
                )
            )

            TradeMessages.TradeUserInputMessage.CancelTradeAtAnyStage -> tradeSender(
                TradeInternalMessages.UserInputMessage.CancelTradeUser
            )

            is TradeMessages.TradeUserInputMessage.AdvertiseBuy -> tradeSender(
                TradeInternalMessages.UserInputMessage.AdvertiseBuy(tradeMessage.gameResourceName)
            )

            is TradeMessages.TradeUserInputMessage.AdvertiseSell -> tradeSender(
                TradeInternalMessages.UserInputMessage.AdvertiseSell(tradeMessage.gameResourceName)
            )
        }
    }

    suspend fun syncAdvertisement(gameSessionId: GameSessionId, senderId: PlayerId) {
        tradeInternalMessageProducer.sendMessage(
            gameSessionId,
            senderId,
            TradeInternalMessages.SystemInputMessage.SyncAdvertisement
        )
    }

    suspend fun cancelAllPlayerTrades(gameSessionId: GameSessionId, playerId: PlayerId) = listOf(
        TradeInternalMessages.UserInputMessage.CancelTradeUser,
        TradeInternalMessages.UserInputMessage.StopAdvertisement
    ).forEach {
        tradeInternalMessageProducer.sendMessage(
            gameSessionId,
            playerId,
            it
        )
    }
}
