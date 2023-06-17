package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
sealed interface MessageADT {
    @Serializable
    sealed interface UserInputMessage : MessageADT {

        @Serializable
        sealed interface TradeMessage : UserInputMessage {
            @Serializable
            @SerialName("tradeBid")
            data class TradeBidMessage(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeMessage

            @Serializable
            @SerialName("tradeStart")
            data class TradeStartMessage(val receiverId: PlayerId) : TradeMessage

            @Serializable
            sealed interface ChangeStateMessage : TradeMessage {
                @Serializable
                @SerialName("tradeStartAck")
                data class TradeStartAckMessage(val receiverId: PlayerId) : ChangeStateMessage

                @Serializable
                @SerialName("tradeFinish")
                data class TradeFinishMessage(val finalBid: TradeBid, val receiverId: PlayerId) : ChangeStateMessage

                @Serializable
                @SerialName("tradeCancel")
                data class TradeCancelMessage(val receiverId: PlayerId) : ChangeStateMessage
            }
        }

        @Serializable
        sealed interface WorkshopChoosing : UserInputMessage {
            @Serializable
            @SerialName("workshop/start")
            object WorkshopChoosingStart : WorkshopChoosing

            @Serializable
            @SerialName("workshop/stop")
            object WorkshopChoosingStop : WorkshopChoosing
        }
    }

    @Serializable
    sealed interface SystemInputMessage : MessageADT {

        @Serializable
        sealed interface WorkshopNotification : SystemInputMessage {
            @Serializable
            @SerialName("notification/workshop/start")
            data class WorkshopChoosingStart(val playerId: PlayerId) : WorkshopNotification

            @Serializable
            @SerialName("notification/workshop/stop")
            data class WorkshopChoosingStop(val playerId: PlayerId) : WorkshopNotification
        }

        @Serializable
        @SerialName("notification/generic")
        data class MulticastMessage(val message: String, val senderId: PlayerId) :
            SystemInputMessage

        @Serializable
        @SerialName("notification/tradeStart")
        data class TradeStart(val playerId: PlayerId) : SystemInputMessage

        @Serializable
        @SerialName("notification/tradeEnd")
        data class TradeEnd(val playerId: PlayerId) : SystemInputMessage

        @Serializable
        sealed interface AutoCancelNotification : SystemInputMessage {

            fun getCanceledMessage(): SystemInputMessage

            @Serializable
            @SerialName("notification/travelStart")
            data class TravelStart(
                val playerId: PlayerId,
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification {
                override fun getCanceledMessage(): SystemInputMessage = CancelMessages.TravelEnd(playerId)
            }

            @Serializable
            @SerialName("notification/productionStart")
            data class ProductionStart(
                val playerId: PlayerId,
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification {
                override fun getCanceledMessage(): SystemInputMessage = CancelMessages.ProductionEnd(playerId)
            }
        }

        @Serializable
        sealed interface CancelMessages : SystemInputMessage {
            @Serializable
            @SerialName("notification/travelEnd")
            data class TravelEnd(val playerId: PlayerId) : CancelMessages

            @Serializable
            @SerialName("notification/productionEnd")
            data class ProductionEnd(val playerId: PlayerId) : CancelMessages
        }
    }

    @Serializable
    sealed interface OutputMessage : MessageADT {

        @Serializable
        @SerialName("tradeServerAck")
        data class TradeAckMessage(val myTurn: Boolean, val otherTrader: PlayerEquipment, val receiverId: PlayerId) :
            OutputMessage

        @Serializable
        @SerialName("tradeServerFinish")
        data class TradeFinishMessage(val receiverId: PlayerId) : OutputMessage

        @Serializable
        @SerialName("userBusy")
        data class UserBusyMessage(val reason: String, val receiverId: PlayerId) : OutputMessage
    }
}
