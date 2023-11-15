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
    fun travelName(): Option<TravelName>
    fun busy(): Boolean = false

    @Serializable
    @SerialName("NoCoopState")
    object NoCoopState : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> NoCoopState.right()

            is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> NoCoopState.right()

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
                none()
            ).right()

            is CoopInternalMessages.SystemOutputMessage.ProposeCompanySystem -> NoCoopState.right()

            is CoopInternalMessages.UserInputMessage.StartSimpleTravel -> NoCoopState.right()

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

            else -> "Coop message not valid while in NoCoopState $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
        override fun travelName(): Option<TravelName> = none()
    }

    @Serializable
    @SerialName("GatheringResources")
    data class GatheringResources(
        val myId: PlayerId,
        val travelName: TravelName,
        val negotiatedBid: OptionS<Pair<PlayerId, ResourcesDecideValues>>,
    ) : CoopStates, TravelSet {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> if (negotiatedBid.isSome()) {
                GatheringResources(myId, travelName, none()).right()
            } else {
                NoCoopState.right()
            }

            is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> if (negotiatedBid.isSome()) {
                GatheringResources(myId, travelName, none()).right()
            } else {
                NoCoopState.right()
            }

            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> NoCoopState.right()

            is CoopInternalMessages.SystemOutputMessage.CancelPlanningAtAnyStage -> if (negotiatedBid.isSome()) {
                GatheringResources(myId, travelName, none()).right()
            } else {
                "Cancel planning message not valid while in coop with nobody".left()
            }

            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (negotiatedBid.isSome()) {
                "Changing planning destinitation is not allowed if you are in coop with someone".left()
            } else {
                GatheringResources(
                    myId,
                    coopMessage.travelName,
                    none()
                ).right()
            }

            is CoopInternalMessages.UserInputMessage.StartAdvertisingCoop -> negotiatedBid.map {
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
                            travelName.toOption()
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

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSystem -> negotiatedBid.map {
                GatheringResources(
                    myId,
                    travelName,
                    negotiatedBid
                ).right()
            }.getOrElse { "Coop message not valid while in GatheringResources with nobody $coopMessage".left() }

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSingleSystem -> negotiatedBid.map {
                "Coop message not valid while in GatheringResources with someone $coopMessage".left()
            }.getOrElse {
                GatheringResources(
                    myId,
                    travelName,
                    negotiatedBid
                ).right()
            }

            is CoopInternalMessages.UserInputMessage.StartPlanningTravel -> negotiatedBid.map {
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

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

            else -> "Coop message not valid while in GatheringResources $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = negotiatedBid.map { it.first }
        override fun travelName(): Option<TravelName> = travelName.toOption()
        override fun traveller(): PlayerId = negotiatedBid.map { it.second.travelerId }.getOrElse { myId }
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("WaitingForCompany")
    data class WaitingForCompany(
        val myId: PlayerId,
        val travelName: TravelName,
        val secondSide: OptionS<PlayerId>
    ) : CoopStates, TravelSet {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> GatheringResources(
                myId,
                travelName,
                none(),
            ).right()

            is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> NoCoopState.right()

            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (coopMessage.myId == myId) {
                GatheringResources(myId, coopMessage.travelName, none()).right()
            } else {
                "Player $myId is not a proper sender in $coopMessage".left()
            }

            CoopInternalMessages.UserInputMessage.StartAdvertisingCoop -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            CoopInternalMessages.UserInputMessage.StopAdvertisingCoop -> GatheringResources(
                myId,
                travelName,
                none()
            ).right()

            is CoopInternalMessages.UserInputMessage.JoinPlanningAckUser -> if (coopMessage.joiningReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.joiningSender,
                    travelName,
                    travelName.toOption()
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
                    travelName.toOption()
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
                            travelName.toOption()
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

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSingleSystem -> WaitingForCompany(
                myId,
                travelName,
                secondSide
            ).right()

            is CoopInternalMessages.UserInputMessage.StartPlanningTravel -> if (coopMessage.myId == myId) {
                if (coopMessage.travelName != travelName) {
                    "Travel from message varies from travel in state: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoCoopState.right()
                }
            } else {
                "Player $myId is not sender of message $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

            else -> "Coop message not valid while in WaitingForCompany $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = secondSide
        override fun travelName(): Option<TravelName> = travelName.toOption()
        override fun traveller(): PlayerId = myId
        override fun busy(): Boolean = false
    }

    @Serializable
    @SerialName("WaitingForOwnerAnswer")
    data class WaitingForOwnerAnswer(
        val myId: PlayerId,
        val ownerId: PlayerId
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> NoCoopState.right()

            is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> NoCoopState.right()

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
                ResourcesDecide.ResourceNegotiatingFirstActive(myId, ownerId, coopMessage.travelName, none()).right()
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
                    none()
                ).right()
            } else {
                "Player $myId is not a proper receiver in $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.StartSimpleTravel -> WaitingForOwnerAnswer(myId, ownerId).right()

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

            else -> "Coop message not valid while in WaitingForOwnerAnswer $coopMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = ownerId.toOption()
        override fun travelName(): Option<TravelName> = none()
    }

    @Serializable
    sealed interface ResourcesDecide : CoopStates {

        @Serializable
        @SerialName("ResourceNegotiatingFirstActive")
        data class ResourceNegotiatingFirstActive(
            val myId: PlayerId,
            val passiveSide: PlayerId,
            val travelName: TravelName,
            val previousTravelName: OptionS<TravelName>
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideUser -> if (coopMessage.bidSender == myId && coopMessage.bidReceiver == passiveSide) {
                    ResourceNegotiatingPassive(
                        myId,
                        passiveSide,
                        travelName,
                        coopMessage.bid,
                        previousTravelName
                    ).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = passiveSide.toOption()
            override fun travelName(): Option<TravelName> = travelName.toOption()
        }

        @Serializable
        @SerialName("ResourceNegotiatingFirstPassive")
        data class ResourceNegotiatingFirstPassive(
            val myId: PlayerId,
            val activeSide: PlayerId,
            val travelName: TravelName,
            val previousTravelName: OptionS<TravelName>
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem -> if (coopMessage.bidReceiver == myId && coopMessage.bidSender == activeSide) {
                    ResourceNegotiatingActive(myId, activeSide, travelName, previousTravelName).right()
                } else {
                    "Player $myId is not a proper receiver in $coopMessage".left()
                }

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = activeSide.toOption()
            override fun travelName(): Option<TravelName> = travelName.toOption()
        }

        @Serializable
        @SerialName("ResourceNegotiatingActive")
        data class ResourceNegotiatingActive(
            val myId: PlayerId,
            val passiveSide: PlayerId,
            val travelName: TravelName,
            val previousTravelName: OptionS<TravelName>
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideUser -> if (coopMessage.bidSender == myId && coopMessage.bidReceiver == passiveSide) {
                    ResourceNegotiatingPassive(
                        myId,
                        passiveSide,
                        travelName,
                        coopMessage.bid,
                        previousTravelName
                    ).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser -> if (coopMessage.finishSender == myId && coopMessage.finishReceiver == passiveSide) {
                    GatheringResources(myId, travelName, (passiveSide to coopMessage.bid).toOption()).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = passiveSide.toOption()
            override fun travelName(): Option<TravelName> = travelName.toOption()
        }

        @Serializable
        @SerialName("ResourceNegotiatingPassive")
        data class ResourceNegotiatingPassive(
            val myId: PlayerId,
            val activeSide: PlayerId,
            val travelName: TravelName,
            val sentBid: ResourcesDecideValues,
            val previousTravelName: OptionS<TravelName>
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoCoopState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem -> if (coopMessage.bidReceiver == myId && coopMessage.bidSender == activeSide) {
                    ResourceNegotiatingActive(myId, activeSide, travelName, previousTravelName).right()
                } else {
                    "Player $myId is not a proper receiver in $coopMessage".left()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem -> if (coopMessage.finishReceiver == myId && coopMessage.finishSender == activeSide) {
                    GatheringResources(myId, travelName, (activeSide to coopMessage.bid).toOption()).right()
                } else {
                    "Player $myId is not a proper sender in $coopMessage".left()
                }

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoCoopState.right()

                else -> "Coop message not valid while in OwnerResourceNegotiatingFirstActive $coopMessage".left()
            }

            override fun secondPlayer(): Option<PlayerId> = activeSide.toOption()
            override fun travelName(): Option<TravelName> = travelName.toOption()
        }

        override fun busy(): Boolean = true
    }
}

sealed interface TravelSet {
    fun travelName(): Option<TravelName>
    fun traveller(): PlayerId
}
