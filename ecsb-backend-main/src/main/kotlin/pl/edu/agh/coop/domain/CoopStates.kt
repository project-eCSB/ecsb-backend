package pl.edu.agh.coop.domain

import arrow.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.OptionS

@Serializable
sealed interface CoopStates {

    fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates>
    fun secondPlayer(): Option<PlayerId>
    fun busy(): Boolean = false

    @Serializable
    @SerialName("NoCoopState")
    object NoCoopState : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.StartPlanning -> GatheringResources(
                coopMessage.myId,
                coopMessage.travelName,
                none()
            ).right()

            is CoopInternalMessages.UserInputMessage.JoinPlanningUser -> WaitingForOwnerAnswer(
                coopMessage.joiningSender,
                coopMessage.joiningReceiver
            ).right()

            is CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser -> ResourcesDecide.ResourceNegotiatingFirstPassive(
                coopMessage.proposeReceiver,
                coopMessage.proposeSender,
                coopMessage.travelName,
                false
            ).right()

            is CoopInternalMessages.SystemOutputMessage.ProposeCompanySystem -> NoCoopState.right()
            else -> "Coop message not valid while in NoCoopState $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
    }

    @Serializable
    @SerialName("GatheringResources")
    data class GatheringResources(
        val myId: PlayerId,
        val travelName: TravelName,
        val negotiatedBid: OptionS<Pair<PlayerId, ResourcesDecideValues>>,
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> if (negotiatedBid.isSome()) {
                GatheringResources(myId, travelName, none()).right()
            } else {
                "Cancel coop message not valid while in coop with nobody".left()
            }

            is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> if (negotiatedBid.isSome()) {
                GatheringResources(myId, travelName, none()).right()
            } else {
                "Cancel coop message not valid while in coop with nobody".left()
            }

            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> NoCoopState.right()

            is CoopInternalMessages.SystemOutputMessage.CancelPlanningAtAnyStage -> if (negotiatedBid.isSome()) {
                GatheringResources(myId, travelName, none()).right()
            } else {
                "Cancel planning message not valid while in coop with nobody".left()
            }

            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (coopMessage.myId == myId) {
                GatheringResources(
                    myId,
                    coopMessage.travelName,
                    none()
                ).right()
            } else {
                "Player $myId is not a proper sender in $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.FindCompanyForPlanning -> negotiatedBid.map {
                "You are already in company with ${it.first}".left()
            }.getOrElse {
                WaitingForCompany(
                    myId,
                    travelName,
                    none()
                ).right()
            }

            is CoopInternalMessages.UserInputMessage.ProposeCompanyUser -> negotiatedBid.map { "Player $myId is already in coop with ${it.first}".left() }
                .getOrElse {
                    if (coopMessage.proposeSender == myId && coopMessage.travelName == travelName) {
                        WaitingForCompany(
                            myId,
                            travelName,
                            coopMessage.proposeReceiver.toOption()
                        ).right()
                    } else {
                        "Player $myId is not a proper sender in $coopMessage".left()
                    }
                }

            is CoopInternalMessages.SystemOutputMessage.ProposeCompanySystem -> negotiatedBid.map { "Player $myId is already in coop with ${it.first}".left() }
                .getOrElse {
                    if (coopMessage.proposeReceiver == myId) {
                        GatheringResources(myId, travelName, none()).right()
                    } else {
                        "Player $myId is not a proper receiver in $coopMessage".left()
                    }
                }

            is CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser -> negotiatedBid.map { "Player $myId is already in coop with ${it.first}".left() }
                .getOrElse {
                    if (coopMessage.proposeReceiver == myId) {
                        ResourcesDecide.ResourceNegotiatingFirstPassive(
                            myId,
                            coopMessage.proposeSender,
                            coopMessage.travelName,
                            false
                        ).right()
                    } else {
                        "Player $myId is not a proper receiver in $coopMessage".left()
                    }
                }

            is CoopInternalMessages.SystemOutputMessage.ProposeCompanyAckSystem -> negotiatedBid.map {
                "Player $myId is already in coop with ${it.first}".left()
            }.getOrElse { "Coop message not valid while in GatheringResources with nobody $coopMessage".left() }

            is CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem -> GatheringResources(
                myId,
                travelName,
                negotiatedBid
            ).right()

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSystem -> GatheringResources(
                myId,
                travelName,
                negotiatedBid
            ).right()

            is CoopInternalMessages.UserInputMessage.StartTravel -> negotiatedBid.map {
                if (myId != it.second.travelerId) {
                    "$myId tried to travel to $travelName, but it should have benn ${it.second.travelerId}".left()
                } else if (coopMessage.travelName != travelName) {
                    "Travel from message varies from travel in state: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoCoopState.right()
                }
            }.getOrElse { "Coop message not valid while in GatheringResources with nobody $coopMessage".left() }

            is CoopInternalMessages.SystemOutputMessage.StartTravel -> negotiatedBid.map {
                if (it.first != it.second.travelerId) {
                    "${it.first} tried to travel to $travelName, but it should have benn ${it.second.travelerId}".left()
                } else if (coopMessage.travelName != travelName) {
                    "Travel from message varies from travel in state: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoCoopState.right()
                }
            }.getOrElse { "End of travel message not valid while in GatheringResources with nobody".left() }

            else -> "Coop message not valid while in GatheringResources $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = negotiatedBid.map { it.first }

        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("WaitingForCompany")
    data class WaitingForCompany(
        val myId: PlayerId,
        val travelName: TravelName,
        val secondSide: OptionS<PlayerId>
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> GatheringResources(
                myId,
                travelName,
                none(),
            ).right()

            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> NoCoopState.right()

            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (coopMessage.myId == myId) {
                GatheringResources(myId, coopMessage.travelName, none()).right()
            } else {
                "Player $myId is not a proper sender in $coopMessage".left()
            }

            CoopInternalMessages.UserInputMessage.FindCompanyForPlanning -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            CoopInternalMessages.UserInputMessage.StopFindingCompany -> GatheringResources(
                myId,
                travelName,
                none()
            ).right()

            is CoopInternalMessages.UserInputMessage.JoinPlanningAckUser -> if (coopMessage.joiningReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.joiningSender,
                    travelName,
                    true
                ).right()
            } else {
                "Player $myId is not receiver of message $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.JoinPlanningSystem -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            is CoopInternalMessages.UserInputMessage.ProposeCompanyUser -> if (myId == coopMessage.proposeSender && travelName == coopMessage.travelName) {
                WaitingForCompany(
                    myId,
                    travelName,
                    coopMessage.proposeReceiver.toOption()
                ).right()
            } else {
                "Player $myId is not a proper sender or travel is not $travelName in $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ProposeCompanySystem -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            is CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser -> if (coopMessage.proposeReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.proposeSender,
                    coopMessage.travelName,
                    false
                ).right()
            } else {
                "Player $myId is not receiver of message $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ProposeCompanyAckSystem -> if (coopMessage.proposeSender == myId && coopMessage.travelName == travelName) {
                secondSide.map {
                    if (it == coopMessage.proposeReceiver) {
                        ResourcesDecide.ResourceNegotiatingFirstActive(
                            myId,
                            coopMessage.proposeReceiver,
                            travelName,
                            true
                        ).right()
                    } else {
                        "Player ${coopMessage.proposeReceiver} accepted proposal too late".left()
                    }
                }.getOrElse { "Player $myId has not send any proposals yet $coopMessage".left() }
            } else {
                "Player $myId is not a proper sender or travel name is wrong in $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSystem -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            is CoopInternalMessages.UserInputMessage.StartTravel -> if (coopMessage.myId == myId) {
                if (coopMessage.travelName != travelName) {
                    "Travel from message varies from travel in state: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoCoopState.right()
                }
            } else {
                "Player $myId is not sender of message $coopMessage".left()
            }

            else -> "Coop message not valid while in WaitingForCompany $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = secondSide

        override fun busy(): Boolean = false
    }

    @Serializable
    @SerialName("WaitingForOwnerAnswer")
    data class WaitingForOwnerAnswer(
        val myId: PlayerId,
        val ownerId: PlayerId
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (coopMessage.myId == myId) {
                GatheringResources(
                    myId,
                    coopMessage.travelName,
                    none()
                ).right()
            } else {
                "Player $myId is not a proper sender in $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.JoinPlanningUser -> if (coopMessage.joiningSender == myId) {
                WaitingForOwnerAnswer(myId, coopMessage.joiningReceiver).right()
            } else {
                "Player $myId is not a proper sender in $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.JoinPlanningAckSystem -> if (coopMessage.joiningSenderId == myId && coopMessage.joiningReceiverId == ownerId) {
                ResourcesDecide.ResourceNegotiatingFirstActive(myId, ownerId, coopMessage.travelName, false).right()
            } else {
                "Player $myId is not a proper receiver in $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ProposeCompanySystem -> WaitingForOwnerAnswer(
                myId,
                ownerId
            ).right()

            is CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser -> if (coopMessage.proposeReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.proposeSender,
                    coopMessage.travelName,
                    false
                ).right()
            } else {
                "Player $myId is not a proper receiver in $coopMessage".left()
            }

            else -> "Coop message not valid while in WaitingForOwnerAnswer $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = ownerId.toOption()

        override fun busy(): Boolean = false
    }

    @Serializable
    sealed interface ResourcesDecide : CoopStates {

        @Serializable
        @SerialName("ResourceNegotiatingFirstActive")
        data class ResourceNegotiatingFirstActive(
            val myId: PlayerId,
            val passiveSide: PlayerId,
            val travelName: TravelName,
            val amIOwner: Boolean
        ) : ResourcesDecide {
            override fun travelName(): TravelName = travelName

            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideUser -> if (coopMessage.bidSender == myId && coopMessage.bidReceiver == passiveSide) {
                    ResourceNegotiatingPassive(myId, passiveSide, travelName, coopMessage.bid, amIOwner).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = passiveSide.toOption()
        }

        @Serializable
        @SerialName("ResourceNegotiatingFirstPassive")
        data class ResourceNegotiatingFirstPassive(
            val myId: PlayerId,
            val activeSide: PlayerId,
            val travelName: TravelName,
            val amIOwner: Boolean
        ) : ResourcesDecide {
            override fun travelName(): TravelName = travelName

            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem -> if (coopMessage.bidReceiver == myId && coopMessage.bidSender == activeSide) {
                    ResourceNegotiatingActive(myId, activeSide, travelName, amIOwner).right()
                } else {
                    "Player $myId is not a proper receiver in $coopMessage".left()
                }

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = activeSide.toOption()
        }

        @Serializable
        @SerialName("ResourceNegotiatingActive")
        data class ResourceNegotiatingActive(
            val myId: PlayerId,
            val passiveSide: PlayerId,
            val travelName: TravelName,
            val amIOwner: Boolean
        ) : ResourcesDecide {
            override fun travelName(): TravelName = travelName

            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideUser -> if (coopMessage.bidSender == myId && coopMessage.bidReceiver == passiveSide) {
                    ResourceNegotiatingPassive(myId, passiveSide, travelName, coopMessage.bid, amIOwner).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser -> if (coopMessage.finishSender == myId && coopMessage.finishReceiver == passiveSide) {
                    GatheringResources(myId, travelName, (passiveSide to coopMessage.bid).toOption()).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = passiveSide.toOption()
        }

        @Serializable
        @SerialName("ResourceNegotiatingPassive")
        data class ResourceNegotiatingPassive(
            val myId: PlayerId,
            val activeSide: PlayerId,
            val travelName: TravelName,
            val sentBid: ResourcesDecideValues,
            val amIOwner: Boolean
        ) : ResourcesDecide {
            override fun travelName(): TravelName = travelName

            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> if (amIOwner) {
                    GatheringResources(
                        myId,
                        travelName,
                        none()
                    ).right()
                } else {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem -> if (coopMessage.bidReceiver == myId && coopMessage.bidSender == activeSide) {
                    ResourceNegotiatingActive(myId, activeSide, travelName, amIOwner).right()
                } else {
                    "Player $myId is not a proper receiver in $coopMessage".left()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem -> if (coopMessage.finishReceiver == myId && coopMessage.finishSender == activeSide) {
                    GatheringResources(myId, travelName, (activeSide to coopMessage.bid).toOption()).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = activeSide.toOption()
        }

        override fun busy(): Boolean = true

        fun travelName(): TravelName
    }
}
