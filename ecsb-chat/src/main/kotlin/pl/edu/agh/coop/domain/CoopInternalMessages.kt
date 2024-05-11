package pl.edu.agh.coop.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.OptionS

@Serializable
sealed interface CoopInternalMessages {

    @Serializable
    sealed interface UserInputMessage : CoopInternalMessages {

        @Serializable
        @SerialName("internal/coop/planning/start")
        data class StartPlanning(val myId: PlayerId, val travelName: TravelName) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/advertise/sync")
        object SyncAdvertisement : UserInputMessage

        @Serializable
        @SerialName("internal/coop/advertise/start")
        object StartAdvertisingCoop : UserInputMessage

        @Serializable
        @SerialName("internal/coop/advertise/stop")
        object StopAdvertisingCoop : UserInputMessage

        @Serializable
        @SerialName("internal/coop/planning/simple")
        data class SimpleJoinPlanningUser(val joiningSender: PlayerId, val joiningReceiver: PlayerId) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/planning/simple/ack")
        data class SimpleJoinPlanningAckUser(val joiningReceiver: PlayerId, val joiningSender: PlayerId) :
            UserInputMessage

        @Serializable
        @SerialName("internal/coop/planning/gathering")
        data class GatheringJoinPlanningUser(val joiningSender: PlayerId, val joiningReceiver: PlayerId) :
            UserInputMessage

        @Serializable
        @SerialName("internal/coop/planning/gathering/ack")
        data class GatheringJoinPlanningAckUser(val joiningReceiver: PlayerId, val joiningSender: PlayerId) :
            UserInputMessage

        @Serializable
        @SerialName("internal/coop/planning/propose")
        data class ProposeOwnTravelUser(
            val proposeSender: PlayerId,
            val proposeReceiver: PlayerId,
            val travelName: TravelName
        ) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/planning/propose/ack")
        data class ProposeOwnTravelAckUser(
            val proposeReceiver: PlayerId,
            val proposeSender: PlayerId,
            val travelName: TravelName
        ) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/decide")
        data class ResourcesDecideUser(val bid: ResourcesDecideValues, val message: String) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/decide/ack")
        data class ResourcesDecideAckUser(val bid: ResourcesDecideValues) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/cancel")
        object CancelCoopAtAnyStage : UserInputMessage

        @Serializable
        @SerialName("internal/coop/cancel/negotiation")
        data class CancelNegotiationAtAnyStage(val message: String) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/cancel/planning")
        object CancelPlanningAtAnyStage : UserInputMessage

        @Serializable
        @SerialName("internal/coop/gathered")
        data class ResourcesGatheredUser(
            val travelerId: OptionS<PlayerId>,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/ungathered")
        data class ResourcesUnGatheredUser(
            val secondPlayerId: PlayerId,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/ungathered/single")
        data class ResourcesUnGatheredSingleUser(val equipment: CoopPlayerEquipment) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/travel/planned")
        data class StartPlannedTravel(val myId: PlayerId, val travelName: TravelName) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/travel/simple")
        data class StartSimpleTravel(val myId: PlayerId, val travelName: TravelName) : UserInputMessage

        @Serializable
        @SerialName("internal/coop/exit")
        object ExitGameSession : UserInputMessage

        @Serializable
        @SerialName("internal/coop/remind")
        data class CoopRemind(val receiverId: PlayerId) : UserInputMessage
    }

    @Serializable
    sealed interface SystemOutputMessage : CoopInternalMessages {

        @Serializable
        @SerialName("internal/coop/system/planning/simple")
        data class SimpleJoinPlanningSystem(val joiningSenderId: PlayerId, val joiningReceiverId: PlayerId) :
            SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/planning/simple/ack")
        data class SimpleJoinPlanningAckSystem(
            val joiningReceiverId: PlayerId,
            val joiningSenderId: PlayerId,
            val travelName: TravelName
        ) : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/planning/gathering")
        data class GatheringJoinPlanningSystem(val joiningSenderId: PlayerId, val joiningReceiverId: PlayerId) :
            SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/planning/gathering/ack")
        data class GatheringJoinPlanningAckSystem(
            val joiningReceiver: PlayerId,
            val joiningSender: PlayerId,
            val travelName: TravelName
        ) :
            SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/planning/propose")
        data class ProposeOwnTravelSystem(
            val proposeSender: PlayerId,
            val proposeReceiver: PlayerId,
        ) : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/planning/propose/ack")
        data class ProposeOwnTravelAckSystem(
            val proposeReceiver: PlayerId,
            val proposeSender: PlayerId,
            val travelName: TravelName
        ) : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/decide")
        data class ResourcesDecideSystem(
            val bid: ResourcesDecideValues
        ) : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/decide/ack")
        data class ResourcesDecideAckSystem(
            val finishSender: PlayerId,
            val bid: ResourcesDecideValues,
            val finishReceiver: PlayerId
        ) : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/cancel/coop")
        object CancelCoopAtAnyStage : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/cancel/negotiation")
        object CancelNegotiationAtAnyStage : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/gathered")
        object ResourcesGatheredSystem : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/ungathered")
        data class ResourcesUnGatheredSystem(
            val secondPlayerId: PlayerId,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/ungathered/single")
        data class ResourcesUnGatheredSingleSystem(
            val equipment: CoopPlayerEquipment
        ) : SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/travel/planned")
        data class StartPlannedTravel(val travelName: TravelName) :
            SystemOutputMessage

        @Serializable
        @SerialName("internal/coop/system/remind")
        data class CoopRemind(val senderId: PlayerId) : SystemOutputMessage
    }
}
