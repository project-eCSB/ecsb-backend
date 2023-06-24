package pl.edu.agh.coop.domain

import arrow.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName

@Serializable
sealed interface CoopStates {

    fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates>
    fun secondPlayer(): Option<PlayerId>
    fun busy(): Boolean = false

    @Serializable
    @SerialName("NoCoopState")
    object NoCoopState : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.FindCoop -> StartPending(coopMessage.cityName).right()
            is CoopInternalMessages.FindCoopAck -> ResourcesDecide.Passive(
                coopMessage.proposalSenderId,
                coopMessage.cityName,
                none()
            ).right()

            is CoopInternalMessages.ProposeCoop -> StartRequest(coopMessage.receiverId).right()
            is CoopInternalMessages.ProposeCoopAck -> CityDecide(coopMessage.senderId, none()).right()
            else -> "Coop message not valid while in NoCoopState $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
    }

    @Serializable
    @SerialName("StartPending")
    data class StartPending(val travelName: TravelName) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.SystemInputMessage.FindCoopAck -> if (travelName == coopMessage.cityName) {
                ResourcesDecide.Active(
                    coopMessage.senderId,
                    coopMessage.cityName,
                    none()
                ).right()
            } else {
                "Travel names don't match".left()
            }

            else -> "Coop message not valid while in StartPending $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("StartRequest")
    data class StartRequest(val receiverId: PlayerId) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.ProposeCoopAck -> if (receiverId == coopMessage.senderId) {
                CityDecide(coopMessage.senderId, none()).right()
            } else {
                "Not valid playerId $receiverId != ${coopMessage.senderId} for CityDecide".left()
            }

            else -> "Coop message not valid while in StartRequest $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
        override fun busy(): Boolean = false
    }

    @Serializable
    @SerialName("CityDecide")
    data class CityDecide(val playerId: PlayerId, val currentVotes: CityDecideVotes) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.CityVotes -> CityDecide(playerId, coopMessage.currentVotes).right()
            is CoopInternalMessages.CityVoteAck -> WaitingForSecondAccept(
                playerId,
                coopMessage.travelName,
                currentVotes
            ).right()

            CoopInternalMessages.SystemInputMessage.CityVotes -> this.copy().right()

            is CoopInternalMessages.SystemInputMessage.CityVoteAck -> WaitingForYourAccept(
                playerId,
                coopMessage.travelName,
                currentVotes
            ).right()

            else -> "Coop message not valid while in CityDecide $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = playerId.some()
    }

    @Serializable
    @SerialName("WaitingForYourAccept")
    data class WaitingForYourAccept(
        val playerId: PlayerId,
        val travelName: TravelName,
        val myFinalVotes: CityDecideVotes
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.CityVoteAck -> if (coopMessage.travelName == travelName) {
                ResourcesDecide.Passive(playerId, travelName, none()).right()
            } else {
                "Travels not match when WaitingForYourAccept ${coopMessage.travelName} != $travelName".left()
            }

            is CoopInternalMessages.CityVotes -> CityDecide(playerId, myFinalVotes).right()
            else -> "Coop message not valid while in WaitingForSecondAccept $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = playerId.some()
    }

    @Serializable
    @SerialName("WaitingForSecondAccept")
    data class WaitingForSecondAccept(
        val playerId: PlayerId,
        val travelName: TravelName,
        val myFinalVotes: CityDecideVotes
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.SystemInputMessage.CityVoteAck -> if (coopMessage.travelName == travelName) {
                ResourcesDecide.Active(playerId, travelName, none()).right()
            } else {
                "Travels not match when WaitingForSecondAccept ${coopMessage.travelName} != $travelName".left()
            }

            CoopInternalMessages.SystemInputMessage.CityVotes -> CityDecide(playerId, myFinalVotes).right()
            else -> "Coop message not valid while in WaitingForSecondAccept $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = playerId.some()
    }

    @Serializable
    sealed interface ResourcesDecide : CoopStates {

        @Serializable
        @SerialName("ResourcesDecide/Passive")
        data class Passive(
            val playerId: PlayerId,
            val travelName: TravelName,
            val yourResourcesDecide: ResourcesDecideValues
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()

                is CoopInternalMessages.SystemInputMessage.ResourcesDecideAck -> WaitingForYourAccept(
                    playerId,
                    travelName,
                    yourResourcesDecide
                ).right()

                else -> "Coop message not valid while in ResourcesDecide.Passive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = playerId.some()
        }

        @Serializable
        @SerialName("ResourcesDecide/Active")
        data class Active(
            val playerId: PlayerId,
            val travelName: TravelName,
            val yourResourcesDecide: ResourcesDecideValues
        ) :
            ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
                is CoopInternalMessages.ResourcesDecide -> Active(
                    playerId,
                    travelName,
                    coopMessage.resourcesDecideValues
                ).right()

                is CoopInternalMessages.ResourcesDecideAck -> WaitingForSecondAccept(
                    playerId,
                    travelName,
                    coopMessage.resourcesDecideValues
                ).right()

                else -> "Coop message not valid while in ResourcesDecide.Passive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = playerId.some()
        }

        @Serializable
        @SerialName("ResourcesDecide/WaitingForYourAccept")
        data class WaitingForYourAccept(
            val playerId: PlayerId,
            val travelName: TravelName,
            val yourResourcesDecide: ResourcesDecideValues
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
                is CoopInternalMessages.ResourcesDecide -> Passive(
                    playerId,
                    travelName,
                    coopMessage.resourcesDecideValues
                ).right()

                is CoopInternalMessages.ResourcesDecideAck -> ResourcesGathering(
                    playerId,
                    travelName,
                    yourResourcesDecide
                ).right()

                else -> "Coop message not valid while in ResourcesDecide.WaitingForYourAccept $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = playerId.some()
        }

        @Serializable
        @SerialName("ResourcesDecide/WaitingForSecondAccept")
        data class WaitingForSecondAccept(
            val playerId: PlayerId,
            val travelName: TravelName,
            val yourResourcesDecide: ResourcesDecideValues
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
                is CoopInternalMessages.SystemInputMessage.ResourcesDecideAck -> if (yourResourcesDecide == coopMessage.otherPlayerResources) {
                    ResourcesGathering(playerId, travelName, yourResourcesDecide).right()
                } else {
                    "Resources don't match for ack".left()
                }

                is CoopInternalMessages.SystemInputMessage.ResourcesDecide -> Active(
                    playerId,
                    travelName,
                    coopMessage.yourResourcesDecide
                ).right()

                else -> "Coop message not valid while in ResourcesDecide.WaitingForSecondAccept $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = playerId.some()
        }
    }

    @Serializable
    @SerialName("ResourcesGathering")
    data class ResourcesGathering(
        val playerId: PlayerId,
        val travelName: TravelName,
        val resourcesDecideValues: ResourcesDecideValues
    ) :
        CoopStates, WaitingCoopEnd {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            CoopInternalMessages.SystemInputMessage.ResourcesGathered -> if (resourcesDecideValues.map { it.first }
                    .getOrElse { playerId } == playerId
            ) {
                WaitingForCoopEnd(playerId).right()
            } else {
                ActiveTravelPlayer(playerId).right()
            }

            else -> "Coop message not valid while in ResourcesGathering $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = playerId.some()
    }

    @Serializable
    @SerialName("WaitingForCoopEnd")
    data class WaitingForCoopEnd(val playerId: PlayerId) : CoopStates, WaitingCoopEnd {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.SystemInputMessage.EndOfTravelReady -> NoCoopState.right()
            else -> "You have to wait now".left()
        }

        override fun secondPlayer(): Option<PlayerId> = playerId.some()
    }

    @Serializable
    @SerialName("ActiveTravelPlayer")
    data class ActiveTravelPlayer(val playerId: PlayerId) : CoopStates, WaitingCoopEnd {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.SystemInputMessage.TravelDone -> NoCoopState.right()
            else -> "Go to fucking travel...".left()
        }

        override fun secondPlayer(): Option<PlayerId> = playerId.some()
    }
}
