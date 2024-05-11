package pl.edu.agh.trade.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.GameResourceName

@Serializable
sealed interface TradeInternalMessages {
    @Serializable
    sealed interface UserInputMessage : TradeInternalMessages {
        @Serializable
        @SerialName("internal/trade/cancel")
        data class CancelTradeUser(val message: String) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/propose")
        data class ProposeTradeUser(val myId: PlayerId, val proposalReceiverId: PlayerId) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/propose/ack")
        data class ProposeTradeAckUser(val proposalSenderId: PlayerId) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/bid")
        data class TradeBidUser(val tradeBid: TradeBid, val receiverId: PlayerId, val message: String) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/bid/ack")
        data class TradeBidAckUser(val finalBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/minor")
        data class TradeMinorChange(val tradeBid: TradeBid, val receiverId: PlayerId) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/buy")
        data class AdvertiseBuy(val gameResourceName: GameResourceName) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/sell")
        data class AdvertiseSell(val gameResourceName: GameResourceName) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/sync")
        object SyncAdvertisement : UserInputMessage

        @Serializable
        @SerialName("internal/trade/remind")
        data class TradeRemind(val receiverId: PlayerId) : UserInputMessage

        @Serializable
        @SerialName("internal/trade/exit")
        object ExitGameSession : UserInputMessage
    }

    @Serializable
    sealed interface SystemOutputMessage : TradeInternalMessages {
        @Serializable
        @SerialName("internal/trade/system/cancel")
        object CancelTradeSystem : SystemOutputMessage

        @Serializable
        @SerialName("internal/trade/system/propose")
        data class ProposeTradeSystem(val proposalSenderId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("internal/trade/system/propose/ack")
        data class ProposeTradeAckSystem(val proposalReceiverId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("internal/trade/system/bid")
        data class TradeBidSystem(val senderId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("internal/trade/system/bid/ack")
        data class TradeBidAckSystem(val senderId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("internal/trade/system/remind")
        data class TradeRemind(val senderId: PlayerId) : SystemOutputMessage
    }
}
