package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.coop.domain.CityDecideVotes
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.travel.domain.TravelName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
sealed interface ChatMessageADT {
    @Serializable
    sealed interface UserInputMessage : ChatMessageADT {

        @Serializable
        sealed interface WorkshopChoosing : UserInputMessage {
            @Serializable
            @SerialName("workshop/start")
            object WorkshopChoosingStart : WorkshopChoosing

            @Serializable
            @SerialName("workshop/stop")
            object WorkshopChoosingStop : WorkshopChoosing
        }

        @Serializable
        sealed interface TravelChoosing : UserInputMessage {
            @Serializable
            @SerialName("travel/start")
            object TravelChoosingStart : TravelChoosing

            @Serializable
            @SerialName("travel/stop")
            object TravelChoosingStop : TravelChoosing
        }
    }

    @Serializable
    sealed interface SystemInputMessage : ChatMessageADT {

        @Serializable
        sealed interface WorkshopNotification : SystemInputMessage {
            @Serializable
            @SerialName("notification/choosing/workshop/start")
            data class WorkshopChoosingStart(val playerId: PlayerId) : WorkshopNotification

            @Serializable
            @SerialName("notification/choosing/workshop/stop")
            data class WorkshopChoosingStop(val playerId: PlayerId) : WorkshopNotification
        }

        @Serializable
        sealed interface TravelNotification : SystemInputMessage {
            @Serializable
            @SerialName("notification/choosing/travel/start")
            data class TravelChoosingStart(val playerId: PlayerId) : TravelNotification

            @Serializable
            @SerialName("notification/choosing/travel/stop")
            data class TravelChoosingStop(val playerId: PlayerId) : TravelNotification
        }

        @Serializable
        @SerialName("notification/generic")
        data class MulticastMessage(val message: String, val senderId: PlayerId) : SystemInputMessage

        @Serializable
        @SerialName("notification/tradeStart")
        data class NotificationTradeStart(val playerId: PlayerId) : SystemInputMessage

        @Serializable
        @SerialName("notification/tradeEnd")
        data class NotificationTradeEnd(val playerId: PlayerId) : SystemInputMessage

        @Serializable
        @SerialName("notification/coop/decide/start")
        data class NotificationCoopStart(val playerId: PlayerId) : SystemInputMessage

        @Serializable
        @SerialName("notification/coop/decide/stop")
        data class NotificationCoopStop(val playerId: PlayerId) : SystemInputMessage

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

        @Serializable
        @SerialName("userBusy")
        data class UserBusyMessage(val reason: String, val receiverId: PlayerId) : TradeMessages.TradeSystemInputMessage
    }
}

sealed interface TradeMessages {
    sealed interface TradeUserInputMessage : TradeMessages, ChatMessageADT.UserInputMessage {
        @Serializable
        @SerialName("trade/cancel_trade")
        object CancelTradeAtAnyStage : TradeUserInputMessage

        @Serializable
        @SerialName("trade/find_trade")
        data class FindTrade(val tradeBid: TradeBid) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/find_trade_ack")
        data class FindTradeAck(val tradeBid: TradeBid, val proposalSenderId: PlayerId) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/propose_trade")
        data class ProposeTradeMessage(val proposalReceiverId: PlayerId) : TradeUserInputMessage, ChatMessageADT.SystemInputMessage

        @Serializable
        @SerialName("trade/propose_trade_ack")
        data class ProposeTradeAckMessage(val proposalSenderId: PlayerId) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/trade_bid")
        data class TradeBidMessage(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeUserInputMessage, ChatMessageADT.SystemInputMessage

        @Serializable
        @SerialName("trade/trade_bid_ack")
        data class TradeBidAckMessage(val finalBid: TradeBid, val receiverId: PlayerId) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/minor_change")
        data class TradeMinorChange(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeUserInputMessage
    }

    sealed interface TradeSystemInputMessage : TradeMessages, ChatMessageADT.SystemInputMessage {
        @Serializable
        @SerialName("trade/server_cancel_trade")
        object CancelTradeAtAnyStage : TradeSystemInputMessage

        @Serializable
        @SerialName("trade/searching_for_trade")
        data class SearchingForTrade(val tradeBid: TradeBid, val playerId: PlayerId) : TradeSystemInputMessage

        @Serializable
        @SerialName("trade/server_start_trade")
        data class TradeAckMessage(val myTurn: Boolean, val otherTrader: PlayerEquipment, val receiverId: PlayerId) : TradeSystemInputMessage

        @Serializable
        @SerialName("trade/server_start_predefined_trade")
        data class PredefinedTradeAckMessage(
            val myTurn: Boolean,
            val tradeBid: TradeBid,
            val otherTrader: PlayerEquipment,
            val receiverId: PlayerId
        ) : TradeSystemInputMessage

        @Serializable
        @SerialName("trade/server_finish_trade")
        data class TradeFinishMessage(val receiverId: PlayerId) : TradeSystemInputMessage
    }
}

sealed interface CoopMessages {
    sealed interface CoopUserInputMessage : CoopMessages, ChatMessageADT.UserInputMessage {
        @Serializable
        @SerialName("coop/find_coop")
        data class FindCoop(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/find_coop_ack")
        data class FindCoopAck(val travelName: TravelName, val playerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/city_decide/ack")
        data class CityDecideAck(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/city_decide/change")
        data class CityDecide(val playerVotes: CityDecideVotes) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide_ack")
        data class ResourceDecideAck(val resources: ResourcesDecideValues) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide")
        data class ResourceDecideChange(val resources: ResourcesDecideValues) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/cancel_coop")
        object CancelCoopAtAnyStage : CoopUserInputMessage
    }

    sealed interface CoopSystemInputMessage : CoopMessages, ChatMessageADT.SystemInputMessage {
        @Serializable
        @SerialName("notification/coop/searching_for_coop")
        data class SearchingForCoop(val travelName: TravelName, val playerId: PlayerId) : CoopSystemInputMessage

        @Serializable
        @SerialName("coop/system/propose_coop")
        data class ProposeCoop(val receiverId: PlayerId) : CoopSystemInputMessage

        @Serializable
        @SerialName("coop/system/propose_coop_ack")
        data class ProposeCoopAck(val proposalSenderId: PlayerId) : CoopSystemInputMessage

        @Serializable
        @SerialName("coop/system/resource_decide_ack")
        data class ResourceDecideAck(val resources: ResourcesDecideValues, val receiverId: PlayerId) :
            CoopSystemInputMessage

        @Serializable
        @SerialName("coop/system/resource_decide")
        data class ResourceDecide(val resourcesDecide: ResourcesDecideValues, val receiverId: PlayerId) :
            CoopSystemInputMessage

        @Serializable
        @SerialName("notification/coop/cancel_coop")
        object CancelCoopAtAnyStage : CoopSystemInputMessage

        @Serializable
        @SerialName("coop/system/city_decide/ack")
        data class CityDecideAck(val travelName: TravelName, val receiverId: PlayerId) : CoopSystemInputMessage

        @Serializable
        @SerialName("coop/system/city_decide/change")
        data class CityDecide(val playerVotes: CityDecideVotes, val receiverId: PlayerId) : CoopSystemInputMessage
    }
}
