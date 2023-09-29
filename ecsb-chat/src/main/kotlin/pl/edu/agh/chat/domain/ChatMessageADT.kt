package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.coop.domain.CityDecideVotes
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradePlayerEquipment
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
sealed interface ChatMessageADT {
    @Serializable
    sealed interface UserInputMessage : ChatMessageADT {

        @Serializable
        @SerialName("user/clicked")
        data class UserClickedOn(val name: PlayerId) : UserInputMessage

        @Serializable
        sealed interface WorkshopChoosing : UserInputMessage {
            @Serializable
            @SerialName("workshop/start")
            object WorkshopChoosingStart : WorkshopChoosing

            @Serializable
            @SerialName("workshop/stop")
            object WorkshopChoosingStop : WorkshopChoosing

            @Serializable
            @SerialName("workshop/change")
            data class WorkshopChoosingChange(val amount: NonNegInt) : WorkshopChoosing
        }

        @Serializable
        sealed interface TravelChoosing : UserInputMessage {
            @Serializable
            @SerialName("travel/start")
            object TravelChoosingStart : TravelChoosing

            @Serializable
            @SerialName("travel/stop")
            object TravelChoosingStop : TravelChoosing

            @Serializable
            @SerialName("travel/change")
            data class TravelChange(val travelName: TravelName) : TravelChoosing
        }
    }

    @Serializable
    sealed interface SystemOutputMessage : ChatMessageADT {

        @Serializable
        sealed interface WorkshopNotification : SystemOutputMessage {
            @Serializable
            @SerialName("notification/choosing/workshop/start")
            data class WorkshopChoosingStart(val playerId: PlayerId) : WorkshopNotification

            @Serializable
            @SerialName("notification/choosing/workshop/stop")
            data class WorkshopChoosingStop(val playerId: PlayerId) : WorkshopNotification
        }

        @Serializable
        sealed interface TravelNotification : SystemOutputMessage {
            @Serializable
            @SerialName("notification/choosing/travel/start")
            data class TravelChoosingStart(val playerId: PlayerId) : TravelNotification

            @Serializable
            @SerialName("notification/choosing/travel/stop")
            data class TravelChoosingStop(val playerId: PlayerId) : TravelNotification
        }

        @Serializable
        @SerialName("notification/generic")
        data class MulticastMessage(val message: String, val senderId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("notification/tradeStart")
        data class NotificationTradeStart(val playerId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("notification/tradeEnd")
        data class NotificationTradeEnd(val playerId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("notification/coop/decide/start")
        data class NotificationCoopStart(val playerId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("notification/coop/decide/stop")
        data class NotificationCoopStop(val playerId: PlayerId) : SystemOutputMessage

        @Serializable
        sealed interface AutoCancelNotification : SystemOutputMessage {

            fun getCanceledMessage(): SystemOutputMessage

            @Serializable
            @SerialName("notification/travelStart")
            data class TravelStart(
                val playerId: PlayerId,
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification {
                override fun getCanceledMessage(): SystemOutputMessage = CancelMessages.TravelEnd(playerId)
            }

            @Serializable
            @SerialName("notification/productionStart")
            data class ProductionStart(
                val playerId: PlayerId,
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification {
                override fun getCanceledMessage(): SystemOutputMessage = CancelMessages.ProductionEnd(playerId)
            }
        }

        @Serializable
        sealed interface CancelMessages : SystemOutputMessage {
            @Serializable
            @SerialName("notification/travelEnd")
            data class TravelEnd(val playerId: PlayerId) : CancelMessages

            @Serializable
            @SerialName("notification/productionEnd")
            data class ProductionEnd(val playerId: PlayerId) : CancelMessages
        }

        @Serializable
        @SerialName("equipment/change")
        data class PlayerResourceChanged(val playerEquipment: PlayerEquipment) : SystemOutputMessage

        @Serializable
        @SerialName("userBusy")
        data class UserBusyMessage(val reason: String, val receiverId: PlayerId) :
            TradeMessages.TradeSystemOutputMessage
    }
}

sealed interface TradeMessages {
    sealed interface TradeUserInputMessage : TradeMessages, ChatMessageADT.UserInputMessage {
        @Serializable
        @SerialName("trade/cancel_trade")
        object CancelTradeAtAnyStage : TradeUserInputMessage

        @Serializable
        @SerialName("trade/advertise_trade")
        data class AdvertiseTradeMessage(val tradeBid: TradeBid) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/advertise_trade_ack")
        data class AdvertiseTradeAckMessage(val tradeBid: TradeBid, val proposalSenderId: PlayerId) :
            TradeUserInputMessage

        @Serializable
        @SerialName("trade/propose_trade")
        data class ProposeTradeMessage(val proposalReceiverId: PlayerId) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/propose_trade_ack")
        data class ProposeTradeAckMessage(val proposalSenderId: PlayerId) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/trade_bid")
        data class TradeBidMessage(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/trade_bid_ack")
        data class TradeBidAckMessage(val finalBid: TradeBid, val receiverId: PlayerId) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/minor_change")
        data class TradeMinorChange(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeUserInputMessage
    }

    sealed interface TradeSystemOutputMessage : TradeMessages, ChatMessageADT.SystemOutputMessage {
        @Serializable
        @SerialName("trade/system/cancel_trade")
        object CancelTradeAtAnyStage : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/propose_trade")
        data class ProposeTradeMessage(val proposalReceiverId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/searching_for_trade")
        data class SearchingForTrade(val playerId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/start_trade")
        data class TradeAckMessage(val myTurn: Boolean, val otherTrader: TradePlayerEquipment, val receiverId: PlayerId) :
            TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/trade_bid")
        data class TradeBidMessage(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/finish_trade")
        data class TradeFinishMessage(val receiverId: PlayerId) : TradeSystemOutputMessage


        @Serializable
        @SerialName("trade/system/second_player_equipment_change")
        data class TradeSecondPlayerEquipmentChange(val secondPlayerEquipment: PlayerEquipment) :
            TradeSystemOutputMessage
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
        @SerialName("coop/propose")
        data class ProposeCoop(val playerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/propose_ack")
        data class ProposeCoopAck(val playerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/city_decide/change")
        data class CityDecide(val playerVotes: CityDecideVotes) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/city_decide/ack")
        data class CityDecideAck(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide/ack")
        data class ResourceDecideAck(val resources: ResourcesDecideValues) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide/change")
        data class ResourceDecideChange(val resources: ResourcesDecideValues) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/cancel_coop")
        object CancelCoopAtAnyStage : CoopUserInputMessage

        @Serializable
        @SerialName("coop/city_decide/renegotiate")
        object RenegotiateCityRequest : CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide/renegotiate")
        object RenegotiateResourcesRequest : CoopUserInputMessage
    }

    sealed interface CoopSystemOutputMessage : CoopMessages, ChatMessageADT.SystemOutputMessage {
        @Serializable
        @SerialName("notification/coop/searching_for_coop")
        data class SearchingForCoop(val travelName: TravelName, val playerId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/propose_coop")
        data class ProposeCoop(val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/propose_coop_ack")
        data class ProposeCoopAck(val proposalSenderId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/resource_decide_ack")
        data class ResourceDecideAck(val resources: ResourcesDecideValues, val receiverId: PlayerId) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/resource_decide")
        data class ResourceDecide(val resourcesDecide: ResourcesDecideValues, val receiverId: PlayerId) :
            CoopSystemOutputMessage


        @Serializable
        @SerialName("coop/system/resource_change")
        data class ResourceChange(val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/cancel_coop")
        object CancelCoopAtAnyStage : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/city_decide/ack")
        data class CityDecideAck(val travelName: TravelName, val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/city_decide/change")
        data class CityDecide(val playerVotes: CityDecideVotes, val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/resource_decide/renegotiate")
        object RenegotiateResourcesRequest : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/city_decide/renegotiate")
        object RenegotiateCityRequest : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel_ready/wait")
        data class WaitForCoopEnd(val travelerId: PlayerId, val travelName: TravelName) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel_ready/go")
        data class GoToGateAndTravel(val waitingPlayerId: PlayerId, val travelName: TravelName) :
            CoopSystemOutputMessage
    }
}
