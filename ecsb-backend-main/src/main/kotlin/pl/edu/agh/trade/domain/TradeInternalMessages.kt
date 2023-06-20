package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

@Serializable
sealed interface TradeInternalMessages {
    @Serializable
    sealed interface UserInputMessage : TradeInternalMessages {
        @Serializable
        object CancelTradeAtAnyStage : UserInputMessage

        @Serializable
        data class FindTrade(val offer: TradeBid) : UserInputMessage

        @Serializable
        data class FindTradeAck(val offer: TradeBid, val bidSenderId: PlayerId) : UserInputMessage

        @Serializable
        data class ProposeTrade(val proposalReceiverId: PlayerId) : UserInputMessage

        @Serializable
        data class ProposeTradeAck(val proposalSenderId: PlayerId) : UserInputMessage

        @Serializable
        data class TradeBidInternal(val tradeBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        data class TradeBidAckInternal(val finalBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        object CancelAllPlayerTrades : UserInputMessage
    }

    @Serializable
    sealed interface SystemInputMessage : TradeInternalMessages {
        @Serializable
        object CancelTradeAtAnyStage : SystemInputMessage

        @Serializable
        data class FindTradeAck(val bidAccepterId: PlayerId, val offer: TradeBid) : SystemInputMessage

        @Serializable
        data class ProposeTrade(val proposalSenderId: PlayerId) : SystemInputMessage

        @Serializable
        data class ProposeTradeAck(val proposalReceiverId: PlayerId) : SystemInputMessage

        @Serializable
        data class TradeBidInternal(val senderId: PlayerId, val tradeBid: TradeBid) : SystemInputMessage

        @Serializable
        data class TradeBidAckInternal(val senderId: PlayerId, val finalBid: TradeBid) : SystemInputMessage
    }
}
