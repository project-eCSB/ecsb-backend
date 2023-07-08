package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

@Serializable
sealed interface TradeInternalMessages {
    @Serializable
    sealed interface UserInputMessage : TradeInternalMessages {
        @Serializable
        object CancelTradeUser : UserInputMessage

        @Serializable
        data class FindTradeUser(val myId: PlayerId, val offer: TradeBid) : UserInputMessage

        @Serializable
        data class FindTradeAckUser(val offer: TradeBid, val bidSenderId: PlayerId) : UserInputMessage

        @Serializable
        data class ProposeTradeUser(val myId: PlayerId, val proposalReceiverId: PlayerId) : UserInputMessage

        @Serializable
        data class ProposeTradeAckUser(val proposalSenderId: PlayerId) : UserInputMessage

        @Serializable
        data class TradeBidUser(val tradeBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        data class TradeBidAckUser(val finalBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        data class TradeMinorChange(val tradeBid: TradeBid, val receiverId: PlayerId) : UserInputMessage
    }

    @Serializable
    sealed interface SystemInputMessage : TradeInternalMessages {
        @Serializable
        object CancelTradeSystem : SystemInputMessage

        @Serializable
        data class FindTradeAckSystem(val bidAccepterId: PlayerId, val offer: TradeBid) : SystemInputMessage

        @Serializable
        data class ProposeTradeSystem(val proposalSenderId: PlayerId) : SystemInputMessage

        @Serializable
        data class ProposeTradeAckSystem(val proposalReceiverId: PlayerId) : SystemInputMessage

        @Serializable
        data class TradeBidSystem(val senderId: PlayerId, val tradeBid: TradeBid) : SystemInputMessage

        @Serializable
        data class TradeBidAckSystem(val senderId: PlayerId, val finalBid: TradeBid) : SystemInputMessage
    }
}
