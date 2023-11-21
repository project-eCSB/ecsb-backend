package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.equipment.domain.PlayerEquipment
import pl.edu.agh.time.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.trade.domain.AdvertiseDto
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.PosInt
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
        @SerialName("notification/sync")
        object SyncAdvertisement : UserInputMessage

        @Serializable
        sealed interface WorkshopMessages : UserInputMessage {
            @Serializable
            @SerialName("workshop/choosing/start")
            object WorkshopChoosingStart : WorkshopMessages

            @Serializable
            @SerialName("workshop/choosing/stop")
            object WorkshopChoosingStop : WorkshopMessages

            @Serializable
            @SerialName("workshop/choosing/change")
            data class WorkshopChoosingChange(val amount: NonNegInt) : WorkshopMessages

            @Serializable
            @SerialName("workshop/start")
            data class WorkshopStart(val amount: PosInt) : WorkshopMessages
        }

        @Serializable
        sealed interface TravelChoosing : UserInputMessage {
            @Serializable
            @SerialName("travel/choosing/start")
            object TravelChoosingStart : TravelChoosing

            @Serializable
            @SerialName("travel/choosing/stop")
            object TravelChoosingStop : TravelChoosing

            @Serializable
            @SerialName("travel/choosing/change")
            data class TravelChange(val travelName: TravelName) : TravelChoosing
        }
    }

    @Serializable
    sealed interface SystemOutputMessage : ChatMessageADT {

        @Serializable
        sealed interface WorkshopMessages : SystemOutputMessage {
            @Serializable
            @SerialName("notification/choosing/workshop/start")
            object WorkshopChoosingStart : WorkshopMessages

            @Serializable
            @SerialName("notification/choosing/workshop/stop")
            object WorkshopChoosingStop : WorkshopMessages

            @Serializable
            @SerialName("workshop/accept")
            data class WorkshopAccept(val time: TimestampMillis) : WorkshopMessages

            @Serializable
            @SerialName("workshop/deny")
            data class WorkshopDeny(val reason: String) : WorkshopMessages
        }

        @Serializable
        sealed interface TravelChoosing : SystemOutputMessage {
            @Serializable
            @SerialName("notification/choosing/travel/start")
            object TravelChoosingStart : TravelChoosing

            @Serializable
            @SerialName("notification/choosing/travel/stop")
            object TravelChoosingStop : TravelChoosing
        }

        @Serializable
        @SerialName("notification/generic")
        data class MulticastMessage(val message: String, val senderId: PlayerId) : SystemOutputMessage

        @Serializable
        sealed interface AutoCancelNotification : SystemOutputMessage {

            fun getCanceledMessage(): SystemOutputMessage

            @Serializable
            @SerialName("notification/travel/start")
            data class TravelStart(
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification {
                override fun getCanceledMessage(): SystemOutputMessage = CancelMessages.TravelEnd
            }

            @Serializable
            @SerialName("notification/production/start")
            data class ProductionStart(
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification {
                override fun getCanceledMessage(): SystemOutputMessage = CancelMessages.ProductionEnd
            }
        }

        @Serializable
        sealed interface CancelMessages : SystemOutputMessage {
            @Serializable
            @SerialName("notification/travel/end")
            object TravelEnd : CancelMessages

            @Serializable
            @SerialName("notification/production/end")
            object ProductionEnd : CancelMessages
        }

        @Serializable
        @SerialName("equipment/change")
        data class PlayerResourceChanged(val receiverId: PlayerId, val playerEquipment: PlayerEquipment) :
            SystemOutputMessage

        @Serializable
        @SerialName("user_warning")
        data class UserWarningMessage(val reason: String, val receiverId: PlayerId) : SystemOutputMessage

        @Serializable
        @SerialName("queue/processed")
        data class QueueEquipmentChangePerformed(
            val receiverId: PlayerId,
            val context: String,
            val money: OptionS<Money>,
            val resources: OptionS<NonEmptyMap<GameResourceName, NonNegInt>>
        ) : SystemOutputMessage
    }
}

sealed interface TradeMessages {
    sealed interface TradeUserInputMessage : TradeMessages, ChatMessageADT.UserInputMessage {

        @Serializable
        @SerialName("trade/buy")
        data class AdvertiseBuy(val gameResourceName: GameResourceName) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/sell")
        data class AdvertiseSell(val gameResourceName: GameResourceName) : TradeUserInputMessage

        @Serializable
        @SerialName("trade/cancel_trade")
        object CancelTradeAtAnyStage : TradeUserInputMessage

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
        data class CancelTradeAtAnyStage(val receiverId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/propose_trade")
        data class ProposeTradeMessage(val proposalReceiverId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/start_trade")
        data class TradeAckMessage(val myTurn: Boolean, val receiverId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/trade_bid")
        data class TradeBidMessage(val tradeBid: TradeBid, val receiverId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("trade/system/finish_trade")
        data class TradeFinishMessage(val receiverId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/buy")
        data class AdvertiseBuy(val gameResourceName: GameResourceName) :
            TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/sell")
        data class AdvertiseSell(val gameResourceName: GameResourceName) :
            TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/trade/start")
        object NotificationTradeStart : TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/trade/end")
        object NotificationTradeEnd : TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/trade/sync/response")
        data class TradeSyncMessage(val receiverId: PlayerId, val states: OptionS<NonEmptyMap<PlayerId, AdvertiseDto>>) : TradeSystemOutputMessage
    }
}

sealed interface CoopMessages {
    sealed interface CoopUserInputMessage : CoopMessages, ChatMessageADT.UserInputMessage {

        @Serializable
        @SerialName("coop/start_planning")
        data class StartPlanning(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/advertise_planning/start")
        data class AdvertisePlanning(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/advertise_planning/stop")
        object StopAdvertisingPlanning : CoopUserInputMessage

        @Serializable
        @SerialName("coop/join_planning/simple")
        data class SimpleJoinPlanning(val ownerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/join_planning/simple/ack")
        data class SimpleJoinPlanningAck(val guestId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/join_planning/gathering")
        data class GatheringJoinPlanning(val ownerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/join_planning/gathering/ack")
        data class GatheringJoinPlanningAck(val otherOwnerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/propose_own_travel")
        data class ProposeOwnTravel(val travelName: TravelName, val guestId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/propose_own_travel/ack")
        data class ProposeOwnTravelAck(val travelName: TravelName, val ownerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide")
        data class ResourceDecide(val yourBid: ResourcesDecideValues) :
            CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide/ack")
        data class ResourceDecideAck(val yourBid: ResourcesDecideValues) :
            CoopUserInputMessage

        @Serializable
        @SerialName("coop/cancel_coop")
        object CancelCoopAtAnyStage : CoopUserInputMessage

        @Serializable
        @SerialName("coop/cancel_negotiation")
        object CancelNegotiationAtAnyStage : CoopUserInputMessage

        @Serializable
        @SerialName("coop/cancel_planning")
        object CancelPlanningAtAnyStage : CoopUserInputMessage

        @Serializable
        @SerialName("coop/start_planned_travel")
        data class StartPlannedTravel(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/start_simple_travel")
        data class StartSimpleTravel(val travelName: TravelName) : CoopUserInputMessage
    }

    sealed interface CoopSystemOutputMessage : CoopMessages, ChatMessageADT.SystemOutputMessage {

        @Serializable
        @SerialName("coop/system/start_planning")
        data class StartPlanningSystem(val ownerId: PlayerId, val travelName: TravelName) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/advertise/start")
        data class StartAdvertisingCoop(val travelName: TravelName) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/advertise/stop")
        object StopAdvertisingCoop : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/join_planning/simple")
        data class SimpleJoinPlanning(val ownerId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/join_planning/gathering")
        data class GatheringJoinPlanning(val ownerId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/propose_own_travel")
        data class ProposeOwnTravel(val guestId: PlayerId, val travelName: TravelName) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/negotiation/start")
        data class ResourceNegotiationStart(val receiverId: PlayerId, val myTurn: Boolean, val travelName: TravelName) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/decide/start")
        object NotificationCoopStart : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/negotiation/bid")
        data class ResourceNegotiationBid(val receiverId: PlayerId, val coopBid: ResourcesDecideValues) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/negotiation/finish")
        data class ResourceNegotiationFinish(val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/decide/stop")
        object NotificationCoopStop : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/resource_change")
        data class CoopResourceChange(
            val travelName: TravelName,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel_ready/wait")
        data class WaitForCoopEnd(
            val receiverId: PlayerId,
            val travelerId: PlayerId,
            val travelName: TravelName,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel_ready/go")
        data class GoToGateAndTravel(
            val receiverId: PlayerId,
            val travelName: TravelName,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel/accept")
        data class TravelAccept(val receiverId: PlayerId, val time: TimestampMillis) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel/deny")
        data class TravelDeny(val receiverId: PlayerId, val reason: String) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/finish")
        data class CoopFinish(val receiverId: PlayerId, val travelerId: PlayerId, val travelName: TravelName) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/cancel_coop")
        data class CancelCoopAtAnyStage(val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/cancel_negotiation")
        data class CancelNegotiationAtAnyStage(val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/cancel_planning")
        data class CancelPlanningAtAnyStage(val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/sync/response")
        data class AdvertisingSync(val receiverId: PlayerId, val states: OptionS<NonEmptyMap<PlayerId, TravelName>>) :
            ChatMessageADT.SystemOutputMessage
    }
}

@Serializable
sealed interface TimeMessages {

    @Serializable
    sealed interface TimeUserInputMessage : TimeMessages, ChatMessageADT.UserInputMessage {
        @Serializable
        @SerialName("time/sync_request")
        object GameTimeSyncRequest : TimeUserInputMessage
    }

    @Serializable
    sealed interface TimeSystemOutputMessage : TimeMessages, ChatMessageADT.SystemOutputMessage {
        @Serializable
        @SerialName("time/sync_response")
        data class GameTimeSyncResponse(
            val receiverId: PlayerId,
            val timeLeftSeconds: TimestampMillis,
            val timeTokens: NonEmptyMap<TimeTokenIndex, TimeState>
        ) : TimeSystemOutputMessage

        @Serializable
        @SerialName("time/end")
        object GameTimeEnd : TimeSystemOutputMessage

        @Serializable
        @SerialName("time/remaining")
        data class GameTimeRemaining(val timeLeftSeconds: TimestampMillis) : TimeSystemOutputMessage

        @Serializable
        @SerialName("time/session_regen")
        data class SessionPlayersTokensRefresh(val tokens: NonEmptyMap<PlayerId, NonEmptyMap<TimeTokenIndex, TimeState>>) :
            TimeSystemOutputMessage

        @Serializable
        @SerialName("time/player_regen")
        data class PlayerTokensRefresh(val playerId: PlayerId, val tokens: NonEmptyMap<TimeTokenIndex, TimeState>) :
            TimeSystemOutputMessage
    }
}
