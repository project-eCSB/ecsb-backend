package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
sealed class MessageADT {
    @Serializable
    sealed class UserInputMessage : MessageADT() {

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
    sealed class SystemInputMessage : MessageADT() {

        @Serializable
        @SerialName("notification/generic")
        data class MulticastMessage(val message: String, val senderId: PlayerId) :
            SystemInputMessage()

        @Serializable
        @SerialName("notification/tradeStart")
        data class TradeStart(val playerId: PlayerId) : SystemInputMessage()

        @Serializable
        @SerialName("notification/clearNotification")
        data class ClearNotification(val playerId: PlayerId) : SystemInputMessage()

        @Serializable
        sealed class AutoCancelNotification : SystemInputMessage() {

            abstract fun getCanceledMessage(): SystemInputMessage

            @Serializable
            @SerialName("notification/travelStart")
            data class TravelStart(
                val playerId: PlayerId,
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification() {
                override fun getCanceledMessage(): SystemInputMessage = CancelMessage(playerId, "travelStart")
            }

            @Serializable
            @SerialName("notification/productionStart")
            data class ProductionStart(
                val playerId: PlayerId,
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification() {
                override fun getCanceledMessage(): SystemInputMessage = CancelMessage(playerId, "productionStart")
            }

            @Serializable
            @SerialName("notification/cancel")
            data class CancelMessage(val playerId: PlayerId, val notificationName: String) : SystemInputMessage()
        }
    }

    @Serializable
    sealed class OutputMessage : MessageADT() {

        @Serializable
        @SerialName("tradeServerAck")
        data class TradeAckMessage(val myTurn: Boolean, val otherTrader: PlayerEquipment, val receiverId: PlayerId) :
            OutputMessage()

        @Serializable
        @SerialName("tradeServerFinish")
        data class TradeFinishMessage(val receiverId: PlayerId) : OutputMessage()

        @Serializable
        @SerialName("userBusy")
        data class UserBusyMessage(val reason: String, val receiverId: PlayerId) : OutputMessage()
    }
}
