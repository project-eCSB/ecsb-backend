package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.GameResourceName

@Serializable
sealed interface TradeInternalMessages {
    @Serializable
    sealed interface UserInputMessage : TradeInternalMessages {
        @Serializable
        data class CancelTradeUser(val message: String) : UserInputMessage

        @Serializable
        object StopAdvertisement : UserInputMessage

        @Serializable
        data class ProposeTradeUser(val myId: PlayerId, val proposalReceiverId: PlayerId) : UserInputMessage

        @Serializable
        data class ProposeTradeAckUser(val proposalSenderId: PlayerId) : UserInputMessage

        @Serializable
        data class TradeBidUser(val tradeBid: TradeBid, val receiverId: PlayerId, val message: String) : UserInputMessage

        @Serializable
        data class TradeBidAckUser(val finalBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        data class TradeMinorChange(val tradeBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        data class AdvertiseBuy(val gameResourceName: GameResourceName) : UserInputMessage

        @Serializable
        data class AdvertiseSell(val gameResourceName: GameResourceName) : UserInputMessage

        @Serializable
        object SyncAdvertisement : UserInputMessage

        @Serializable
        data class TradeRemind(val receiverId: PlayerId) : UserInputMessage
    }

    @Serializable
    sealed interface SystemInputMessage : TradeInternalMessages {
        @Serializable
        object CancelTradeSystem : SystemInputMessage

        @Serializable
        data class ProposeTradeSystem(val proposalSenderId: PlayerId) : SystemInputMessage

        @Serializable
        data class ProposeTradeAckSystem(val proposalReceiverId: PlayerId) : SystemInputMessage

        @Serializable
        data class TradeBidSystem(val senderId: PlayerId) : SystemInputMessage

        @Serializable
        data class TradeBidAckSystem(val senderId: PlayerId) : SystemInputMessage

        @Serializable
        data class TradeRemind(val senderId: PlayerId) : SystemInputMessage
    }
}
