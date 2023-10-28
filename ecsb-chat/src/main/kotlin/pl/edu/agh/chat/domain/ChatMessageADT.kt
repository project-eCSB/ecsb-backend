package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.coop.domain.CoopPlayerEquipment
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
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
        sealed interface WorkshopMessages : SystemOutputMessage {
            @Serializable
            @SerialName("notification/choosing/workshop/start")
            data class WorkshopChoosingStart(val playerId: PlayerId) : WorkshopMessages

            @Serializable
            @SerialName("notification/choosing/workshop/stop")
            data class WorkshopChoosingStop(val playerId: PlayerId) : WorkshopMessages

            @Serializable
            @SerialName("workshop/accept")
            data class WorkshopAccept(val time: TimestampMillis) : WorkshopMessages

            @Serializable
            @SerialName("workshop/deny")
            data class WorkshopDeny(val reason: String) : WorkshopMessages
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
        sealed interface AutoCancelNotification : SystemOutputMessage {

            fun getCanceledMessage(): SystemOutputMessage

            @Serializable
            @SerialName("notification/travel/start")
            data class TravelStart(
                val playerId: PlayerId,
                val timeout: Duration = 5.seconds
            ) : AutoCancelNotification {
                override fun getCanceledMessage(): SystemOutputMessage = CancelMessages.TravelEnd(playerId)
            }

            @Serializable
            @SerialName("notification/production/start")
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
            @SerialName("notification/travel/end")
            data class TravelEnd(val playerId: PlayerId) : CancelMessages

            @Serializable
            @SerialName("notification/production/end")
            data class ProductionEnd(val playerId: PlayerId) : CancelMessages
        }

        @Serializable
        @SerialName("equipment/change")
        data class PlayerResourceChanged(val playerEquipment: PlayerEquipment) : SystemOutputMessage

        @Serializable
        @SerialName("user_warning")
        data class UserWarningMessage(val reason: String, val receiverId: PlayerId) : SystemOutputMessage
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
        object CancelTradeAtAnyStage : TradeSystemOutputMessage

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
        data class AdvertiseBuy(val playerId: PlayerId, val gameResourceName: GameResourceName) :
            TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/sell")
        data class AdvertiseSell(val playerId: PlayerId, val gameResourceName: GameResourceName) :
            TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/trade/start")
        data class NotificationTradeStart(val playerId: PlayerId) : TradeSystemOutputMessage

        @Serializable
        @SerialName("notification/trade/end")
        data class NotificationTradeEnd(val playerId: PlayerId) : TradeSystemOutputMessage
    }
}

sealed interface CoopMessages {
    sealed interface CoopUserInputMessage : CoopMessages, ChatMessageADT.UserInputMessage {

        @Serializable
        @SerialName("coop/start_planning")
        data class StartPlanning(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/find_company")
        data class FindCompanyForPlanning(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/stop_finding")
        object StopFindingCompany : CoopUserInputMessage

        @Serializable
        @SerialName("coop/join_planning")
        data class JoinPlanning(val ownerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/join_planning/ack")
        data class JoinPlanningAck(val guestId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/propose_company")
        data class ProposeCompany(val travelName: TravelName, val guestId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/propose_company/ack")
        data class ProposeCompanyAck(val travelName: TravelName, val ownerId: PlayerId) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide")
        data class ResourceDecide(val yourBid: ResourcesDecideValues, val otherPlayerId: PlayerId) :
            CoopUserInputMessage

        @Serializable
        @SerialName("coop/resource_decide/ack")
        data class ResourceDecideAck(val otherPlayerBid: ResourcesDecideValues, val otherPlayerId: PlayerId) :
            CoopUserInputMessage

        @Serializable
        @SerialName("coop/cancel_coop")
        object CancelCoopAtAnyStage : CoopUserInputMessage

        @Serializable
        @SerialName("coop/cancel_planning")
        object CancelPlanningAtAnyStage : CoopUserInputMessage

        @Serializable
        @SerialName("coop/start_planning_travel")
        data class StartPlanningTravel(val travelName: TravelName) : CoopUserInputMessage

        @Serializable
        @SerialName("coop/start_simple_travel")
        data class StartSimpleTravel(val travelName: TravelName) : CoopUserInputMessage
    }

    sealed interface CoopSystemOutputMessage : CoopMessages, ChatMessageADT.SystemOutputMessage {

        @Serializable
        @SerialName("coop/system/start_planning")
        data class StartPlanningSystem(val travelName: TravelName) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/advertise/start")
        data class AdvertiseCompanySearching(val ownerId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/advertise/stop")
        data class StopCompanySearching(val ownerId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/join_planning")
        data class JoinPlanning(val ownerId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/propose_company")
        data class ProposeCompany(val guestId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/negotiation/start")
        data class ResourceNegotiationStart(val myTurn: Boolean, val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/negotiation/bid")
        data class ResourceNegotiationBid(val coopBid: ResourcesDecideValues, val receiverId: PlayerId) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/negotiation/finish")
        object ResourceNegotiationFinish : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/resource_change")
        data class ResourceChange(val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel_ready/wait")
        data class WaitForCoopEnd(val travelerId: PlayerId, val travelName: TravelName) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel_ready/go")
        data class GoToGateAndTravel(val waitingPlayerId: PlayerId, val travelName: TravelName) :
            CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/travel_completed")
        data class TravelCompleted(val travelerId: PlayerId, val travelName: TravelName) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/cancel_coop")
        data class CancelCoopAtAnyStage(val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("coop/system/cancel_planning")
        data class CancelPlanningAtAnyStage(val receiverId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/decide/start")
        data class NotificationCoopStart(val playerId: PlayerId) : CoopSystemOutputMessage

        @Serializable
        @SerialName("notification/coop/decide/stop")
        data class NotificationCoopStop(val playerId: PlayerId) : CoopSystemOutputMessage
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
