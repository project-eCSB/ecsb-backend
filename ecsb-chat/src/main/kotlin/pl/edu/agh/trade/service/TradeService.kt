package pl.edu.agh.trade.service

import arrow.core.partially1
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime

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
            is TradeMessages.TradeUserInputMessage.FindTrade -> sender(
                TradeInternalMessages.UserInputMessage.FindTrade(
                    playerId,
                    tradeMessage.tradeBid
                )
            )

            is TradeMessages.TradeUserInputMessage.FindTradeAck -> sender(
                TradeInternalMessages.UserInputMessage.FindTradeAck(
                    tradeMessage.tradeBid,
                    tradeMessage.proposalSenderId
                )
            )

            is TradeMessages.TradeUserInputMessage.ProposeTradeMessage -> sender(
                TradeInternalMessages.UserInputMessage.ProposeTrade(
                    playerId,
                    tradeMessage.proposalReceiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.ProposeTradeAckMessage -> sender(
                TradeInternalMessages.UserInputMessage.ProposeTradeAck(
                    tradeMessage.proposalSenderId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeBidMessage -> sender(
                TradeInternalMessages.UserInputMessage.TradeBidMsg(
                    tradeMessage.tradeBid,
                    tradeMessage.receiverId
                )
            )

            is TradeMessages.TradeUserInputMessage.TradeBidAckMessage -> sender(
                TradeInternalMessages.UserInputMessage.TradeBidAck(
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

            TradeMessages.TradeUserInputMessage.CancelTradeAtAnyStage -> sender(TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage)
        }
    }

    suspend fun cancelAllPlayerTrades(gameSessionId: GameSessionId, playerId: PlayerId) =
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage
        )
}
