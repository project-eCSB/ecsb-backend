package pl.edu.agh.coop.domain

import arrow.core.*
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName

sealed interface CoopStates {

    fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates>

    object NoCoopState : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.FindCoop -> StartPending(coopMessage.cityName).right()
            is CoopInternalMessages.ProposeCoop -> StartRequest(coopMessage.receiverId).right()
            is CoopInternalMessages.ProposeCoopAck -> CityDecide(coopMessage.senderId, none()).right()
            else -> "Coop message not valid while in NoCoopState $coopMessage".left()
        }
    }

    data class StartPending(val travelName: TravelName) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.SystemInputMessage.FindCoopAck -> if (travelName == coopMessage.cityName) {
                ResourcesGathering(
                    coopMessage.senderId,
                    coopMessage.cityName,
                    none()
                ).right()
            } else {
                "Travel names don't match".left()
            }

            else -> "Coop message not valid while in StartPending $coopMessage".left()
        }
    }

    data class StartRequest(val receiverId: PlayerId) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.ProposeCoopAck -> if (receiverId == coopMessage.senderId) {
                CityDecide(coopMessage.senderId, none()).right()
            } else {
                "Not valid playerId $receiverId != ${coopMessage.senderId} for CityDecide".left()
            }

            else -> "Coop message not valid while in StartRequest $coopMessage".left()
        }
    }

    data class CityDecide(val playerId: PlayerId, val currentVotes: CityDecideVotes) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
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
    }

    data class WaitingForYourAccept(
        val playerId: PlayerId,
        val travelName: TravelName,
        val myFinalVotes: CityDecideVotes
    ) :
        CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.CityVoteAck -> if (coopMessage.travelName == travelName) {
                ResourcesGathering(playerId, travelName, none()).right()
            } else {
                "Travels not match when WaitingForYourAccept ${coopMessage.travelName} != $travelName".left()
            }

            is CoopInternalMessages.CityVotes -> CityDecide(playerId, myFinalVotes).right()
            else -> "Coop message not valid while in WaitingForSecondAccept $coopMessage".left()
        }
    }

    data class WaitingForSecondAccept(
        val playerId: PlayerId,
        val travelName: TravelName,
        val myFinalVotes: CityDecideVotes
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.SystemInputMessage.CityVoteAck -> if (coopMessage.travelName == travelName) {
                ResourcesGathering(playerId, travelName, none()).right()
            } else {
                "Travels not match when WaitingForSecondAccept ${coopMessage.travelName} != $travelName".left()
            }

            CoopInternalMessages.SystemInputMessage.CityVotes -> CityDecide(playerId, myFinalVotes).right()
            else -> "Coop message not valid while in WaitingForSecondAccept $coopMessage".left()
        }
    }

    data class ResourcesGathering(
        val playerId: PlayerId,
        val travelName: TravelName,
        val willTakeCareOf: WillTakeCareOf
    ) :
        CoopStates, WaitingCoopEnd {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            is CoopInternalMessages.WillTakeCareOfMessage -> ResourcesGathering(
                playerId,
                travelName,
                coopMessage.willTakeCareOf
            ).right()

            CoopInternalMessages.SystemInputMessage.ResourcesGathered -> ResourcesGathered(playerId, travelName).right()
            else -> "Coop message not valid while in ResourcesGathering $coopMessage".left()
        }
    }

    data class ResourcesGathered(val playerId: PlayerId, val travelName: TravelName) : CoopStates, WaitingCoopEnd {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
            CoopInternalMessages.StartResourcesDecide -> ResourcesDecide.Active(playerId, travelName, none()).right()
            CoopInternalMessages.SystemInputMessage.StartResourcesPassiveDecide -> ResourcesDecide.Passive(
                playerId,
                travelName,
                none()
            )
                .right()

            else -> "Coop message not valid while in ResourcesGathered $coopMessage".left()
        }
    }

    sealed class ResourcesDecide(
        open val playerId: PlayerId,
        open val travelName: TravelName,
        open val yourResourcesDecide: ResourcesDecideValues
    ) : CoopStates {
        data class Passive(
            override val playerId: PlayerId,
            override val travelName: TravelName,
            override val yourResourcesDecide: ResourcesDecideValues
        ) :
            ResourcesDecide(playerId, travelName, yourResourcesDecide) {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()

                CoopInternalMessages.SystemInputMessage.ResourcesDecideAck -> WaitingForYourAccept(
                    playerId,
                    travelName,
                    yourResourcesDecide
                ).right()

                else -> "Coop message not valid while in ResourcesDecide.Passive $coopMessage".left()
            }
        }

        data class Active(
            override val playerId: PlayerId,
            override val travelName: TravelName,
            override val yourResourcesDecide: ResourcesDecideValues
        ) :
            ResourcesDecide(playerId, travelName, yourResourcesDecide) {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
                is CoopInternalMessages.ResourcesDecide -> Active(
                    playerId,
                    travelName,
                    coopMessage.resourcesDecideValues
                ).right()

                is CoopInternalMessages.ResourcesDecideAck -> WaitingForSecondAccept(
                    playerId,
                    travelName,
                    yourResourcesDecide
                ).right()

                else -> "Coop message not valid while in ResourcesDecide.Passive $coopMessage".left()
            }
        }

        data class WaitingForYourAccept(
            override val playerId: PlayerId,
            override val travelName: TravelName,
            override val yourResourcesDecide: ResourcesDecideValues
        ) : ResourcesDecide(playerId, travelName, yourResourcesDecide) {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
                is CoopInternalMessages.ResourcesDecide -> Passive(
                    playerId,
                    travelName,
                    coopMessage.resourcesDecideValues
                ).right()

                is CoopInternalMessages.ResourcesDecideAck -> if (yourResourcesDecide.map { it.first }
                    .getOrElse { playerId } == playerId
                ) {
                    WaitingForCoopEnd(playerId).right()
                } else {
                    ActiveTravelPlayer(playerId).right()
                }

                else -> "Coop message not valid while in ResourcesDecide.WaitingForYourAccept $coopMessage".left()
            }
        }

        data class WaitingForSecondAccept(
            override val playerId: PlayerId,
            override val travelName: TravelName,
            override val yourResourcesDecide: ResourcesDecideValues
        ) : ResourcesDecide(playerId, travelName, yourResourcesDecide) {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.CancelCoopAtAnyStage -> NoCoopState.right()
                CoopInternalMessages.SystemInputMessage.ResourcesDecideAck -> if (yourResourcesDecide.map { it.first }
                    .getOrElse { playerId } == playerId
                ) {
                    WaitingForCoopEnd(playerId).right()
                } else {
                    ActiveTravelPlayer(playerId).right()
                }

                is CoopInternalMessages.SystemInputMessage.ResourcesDecideReject -> Active(
                    playerId,
                    travelName,
                    coopMessage.yourResourcesDecide
                ).right()

                else -> "Coop message not valid while in ResourcesDecide.WaitingForSecondAccept $coopMessage".left()
            }
        }
    }

    data class WaitingForCoopEnd(val playerId: PlayerId) : CoopStates, WaitingCoopEnd {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.SystemInputMessage.EndOfTravelReady -> NoCoopState.right()
            else -> "You have to wait now".left()
        }
    }

    data class ActiveTravelPlayer(val playerId: PlayerId) : CoopStates, WaitingCoopEnd {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            CoopInternalMessages.SystemInputMessage.TravelDone -> NoCoopState.right()
            else -> "Go to fucking travel...".left()
        }
    }
}
