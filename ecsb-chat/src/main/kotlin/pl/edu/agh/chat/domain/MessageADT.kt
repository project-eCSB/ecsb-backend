package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId

@Serializable
sealed class MessageADT {
    @Serializable
    sealed class UserInputMessage : MessageADT() {

        @Serializable
        @SerialName("multicast")
        data class MulticastMessage(val message: String) : UserInputMessage()

        @Serializable
        sealed class TradeMessage : UserInputMessage() {
            @Serializable
            @SerialName("tradeBid")
            data class TradeBidMessage(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeMessage()

            @Serializable
            @SerialName("tradeStart")
            data class TradeStartMessage(val receiverId: PlayerId) : TradeMessage()

            @Serializable
            sealed class ChangeStateMessage : TradeMessage() {
                @Serializable
                @SerialName("tradeStartAck")
                data class TradeStartAckMessage(val receiverId: PlayerId) : ChangeStateMessage()

                @Serializable
                @SerialName("tradeFinish")
                data class TradeFinishMessage(val finalBid: TradeBid, val receiverId: PlayerId) : ChangeStateMessage()

                @Serializable
                @SerialName("tradeCancel")
                data class TradeCancelMessage(val receiverId: PlayerId) : ChangeStateMessage()
            }
        }
    }

    @Serializable
    sealed class OutputMessage : MessageADT() {

        @Serializable
        @SerialName("tradeServerAck")
        data class TradeAckMessage(val myTurn: Boolean, val otherTrader: PlayerEquipment, val receiverId: PlayerId) : OutputMessage()

        @Serializable
        @SerialName("tradeServerFinish")
        data class TradeFinishMessage(val receiverId: PlayerId) : OutputMessage()

        @Serializable
        @SerialName("userBusy")
        data class UserBusyMessage(val reason: String, val receiverId: PlayerId) : OutputMessage()
    }
}
