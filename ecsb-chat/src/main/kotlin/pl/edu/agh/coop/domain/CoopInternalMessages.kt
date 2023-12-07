package pl.edu.agh.coop.domain

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
        data class StartPlanning(
            val myId: PlayerId,
            val travelName: TravelName
        ) : UserInputMessage

        @Serializable
        object SyncAdvertisement : UserInputMessage

        @Serializable
        object StartAdvertisingCoop : UserInputMessage

        @Serializable
        object StopAdvertisingCoop : UserInputMessage

        @Serializable
        data class SimpleJoinPlanningUser(val joiningSender: PlayerId, val joiningReceiver: PlayerId) : UserInputMessage

        @Serializable
        data class SimpleJoinPlanningAckUser(val joiningReceiver: PlayerId, val joiningSender: PlayerId) :
            UserInputMessage

        @Serializable
        data class GatheringJoinPlanningUser(val joiningSender: PlayerId, val joiningReceiver: PlayerId) :
            UserInputMessage

        @Serializable
        data class GatheringJoinPlanningAckUser(val joiningReceiver: PlayerId, val joiningSender: PlayerId) :
            UserInputMessage

        @Serializable
        data class ProposeOwnTravelUser(
            val proposeSender: PlayerId,
            val proposeReceiver: PlayerId,
            val travelName: TravelName
        ) : UserInputMessage

        @Serializable
        data class ProposeOwnTravelAckUser(
            val proposeReceiver: PlayerId,
            val proposeSender: PlayerId,
            val travelName: TravelName
        ) : UserInputMessage

        @Serializable
        data class ResourcesDecideUser(
            val bid: ResourcesDecideValues
        ) : UserInputMessage

        @Serializable
        data class ResourcesDecideAckUser(
            val bid: ResourcesDecideValues
        ) : UserInputMessage

        @Serializable
        object CancelCoopAtAnyStage : UserInputMessage

        @Serializable
        object CancelNegotiationAtAnyStage : UserInputMessage

        @Serializable
        object CancelPlanningAtAnyStage : UserInputMessage

        @Serializable
        data class ResourcesGatheredUser(
            val travelerId: OptionS<PlayerId>,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) : UserInputMessage

        @Serializable
        data class ResourcesUnGatheredUser(
            val secondPlayerId: PlayerId,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) : UserInputMessage

        @Serializable
        data class ResourcesUnGatheredSingleUser(
            val equipment: CoopPlayerEquipment
        ) : UserInputMessage

        @Serializable
        data class StartPlannedTravel(val myId: PlayerId, val travelName: TravelName) : UserInputMessage

        @Serializable
        data class StartSimpleTravel(val myId: PlayerId, val travelName: TravelName) : UserInputMessage

        @Serializable
        object ExitGameSession : UserInputMessage
    }

    @Serializable
    sealed interface SystemOutputMessage : CoopInternalMessages {

        @Serializable
        object ResourcesGatheredSystem : SystemOutputMessage

        @Serializable
        data class ResourcesUnGatheredSystem(
            val secondPlayerId: PlayerId,
            val equipments: NonEmptyMap<PlayerId, CoopPlayerEquipment>
        ) : SystemOutputMessage

        @Serializable
        data class ResourcesUnGatheredSingleSystem(
            val equipment: CoopPlayerEquipment
        ) : SystemOutputMessage

        @Serializable
        data class ResourcesDecideSystem(
            val bid: ResourcesDecideValues
        ) : SystemOutputMessage

        @Serializable
        data class ResourcesDecideAckSystem(
            val finishSender: PlayerId,
            val bid: ResourcesDecideValues,
            val finishReceiver: PlayerId
        ) :
            SystemOutputMessage

        @Serializable
        data class SimpleJoinPlanningSystem(val joiningSenderId: PlayerId, val joiningReceiverId: PlayerId) :
            SystemOutputMessage

        @Serializable
        data class SimpleJoinPlanningAckSystem(
            val joiningReceiverId: PlayerId,
            val joiningSenderId: PlayerId,
            val travelName: TravelName
        ) : SystemOutputMessage

        @Serializable
        data class GatheringJoinPlanningSystem(val joiningSenderId: PlayerId, val joiningReceiverId: PlayerId) :
            SystemOutputMessage

        @Serializable
        data class GatheringJoinPlanningAckSystem(
            val joiningReceiver: PlayerId,
            val joiningSender: PlayerId,
            val travelName: TravelName
        ) :
            SystemOutputMessage

        @Serializable
        data class ProposeOwnTravelSystem(
            val proposeSender: PlayerId,
            val proposeReceiver: PlayerId,
        ) : SystemOutputMessage

        @Serializable
        data class ProposeOwnTravelAckSystem(
            val proposeReceiver: PlayerId,
            val proposeSender: PlayerId,
            val travelName: TravelName
        ) : SystemOutputMessage

        @Serializable
        object CancelCoopAtAnyStage : SystemOutputMessage

        @Serializable
        object CancelNegotiationAtAnyStage : SystemOutputMessage

        @Serializable
        data class StartPlannedTravel(val travelName: TravelName) :
            SystemOutputMessage
    }
}
