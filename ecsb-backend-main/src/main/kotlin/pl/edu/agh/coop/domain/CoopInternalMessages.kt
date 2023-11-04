package pl.edu.agh.coop.domain

import arrow.core.Either
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap

typealias ErrorOr<T> = Either<String, T>

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
        object FindCompanyForPlanning : UserInputMessage

        @Serializable
        object StopFindingCompany : UserInputMessage

        @Serializable
        data class JoinPlanningUser(val joiningSender: PlayerId, val joiningReceiver: PlayerId) : UserInputMessage

        @Serializable
        data class JoinPlanningAckUser(val joiningReceiver: PlayerId, val joiningSender: PlayerId) :
            UserInputMessage

        @Serializable
        data class ProposeCompanyUser(
            val proposeSender: PlayerId,
            val proposeReceiver: PlayerId,
            val travelName: TravelName
        ) : UserInputMessage

        @Serializable
        data class ProposeCompanyAckUser(
            val proposeReceiver: PlayerId,
            val proposeSender: PlayerId,
            val travelName: TravelName
        ) : UserInputMessage

        @Serializable
        data class ResourcesDecideUser(
            val bidSender: PlayerId,
            val bid: ResourcesDecideValues,
            val bidReceiver: PlayerId
        ) : UserInputMessage

        @Serializable
        data class ResourcesDecideAckUser(
            val finishSender: PlayerId,
            val bid: ResourcesDecideValues,
            val finishReceiver: PlayerId
        ) : UserInputMessage

        @Serializable
        object CancelCoopAtAnyStage : UserInputMessage

        @Serializable
        object CancelPlanningAtAnyStage : UserInputMessage

        @Serializable
        data class ResourcesGatheredUser(val travelerId: PlayerId) : UserInputMessage

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
        data class StartPlanningTravel(val myId: PlayerId, val travelName: TravelName) : UserInputMessage

        @Serializable
        data class StartSimpleTravel(val myId: PlayerId, val travelName: TravelName) : UserInputMessage
    }

    @Serializable
    sealed interface SystemOutputMessage : CoopInternalMessages {

        @Serializable
        data class ResourcesGatheredSystem(val travelerId: PlayerId) : SystemOutputMessage

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
            val bidSender: PlayerId,
            val bidReceiver: PlayerId
        ) : SystemOutputMessage

        @Serializable
        data class ResourcesDecideAckSystem(
            val finishSender: PlayerId,
            val bid: ResourcesDecideValues,
            val finishReceiver: PlayerId
        ) :
            SystemOutputMessage

        @Serializable
        data class JoinPlanningSystem(val joiningSenderId: PlayerId, val joiningReceiverId: PlayerId) :
            SystemOutputMessage

        @Serializable
        data class JoinPlanningAckSystem(
            val joiningReceiverId: PlayerId,
            val joiningSenderId: PlayerId,
            val travelName: TravelName
        ) :
            SystemOutputMessage

        @Serializable
        data class ProposeCompanySystem(
            val proposeSender: PlayerId,
            val proposeReceiver: PlayerId,
        ) : SystemOutputMessage

        @Serializable
        data class ProposeCompanyAckSystem(
            val proposeReceiver: PlayerId,
            val proposeSender: PlayerId,
            val travelName: TravelName
        ) : SystemOutputMessage

        @Serializable
        object CancelCoopAtAnyStage : SystemOutputMessage

        @Serializable
        object CancelPlanningAtAnyStage : SystemOutputMessage

        @Serializable
        data class StartTravel(val travelName: TravelName) :
            SystemOutputMessage
    }
}
