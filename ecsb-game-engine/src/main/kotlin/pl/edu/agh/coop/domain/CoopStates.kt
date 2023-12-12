package pl.edu.agh.coop.domain

import arrow.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.OptionS

typealias ErrorOr<T> = Either<String, T>

@Serializable
sealed interface CoopStates {

    fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates>
    fun secondPlayer(): Option<PlayerId> = none()
    fun travelName(): Option<TravelName> = none()
    fun busy(): Boolean = false

    @Serializable
    @SerialName("NoPlanningState")
    object NoPlanningState : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> this.right()

            is CoopInternalMessages.UserInputMessage.StartPlanning -> GatheringResources(
                coopMessage.myId,
                coopMessage.travelName,
                none()
            ).right()

            is CoopInternalMessages.UserInputMessage.SimpleJoinPlanningUser -> WaitingForOwnerAnswer(
                coopMessage.joiningSender,
                coopMessage.joiningReceiver
            ).right()

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelAckUser -> ResourcesDecide.ResourceNegotiatingFirstPassive(
                coopMessage.proposeReceiver,
                coopMessage.proposeSender,
                coopMessage.travelName,
                none()
            ).right()

            is CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelSystem -> this.right()

            is CoopInternalMessages.UserInputMessage.StartSimpleTravel -> this.right()

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> this.right()

            else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie NoPlanningState".left()
        }
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
                "Użytkownik nie może anulować współpracy, jeśli w niej nie jest".left()
            }

            is CoopInternalMessages.SystemOutputMessage.CancelCoopAtAnyStage -> if (negotiatedBid.isSome()) {
                GatheringResources(myId, travelName, none()).right()
            } else {
                "System nie może anulować współpracy, jeśli gracz $myId w niej nie jest".left()
            }

            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> NoPlanningState.right()

            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (negotiatedBid.isSome()) {
                "Zmiana celu wyprawy nie jest możliwa, jeśli jesteś z kimś we współpracy".left()
            } else {
                GatheringResources(
                    myId,
                    coopMessage.travelName,
                    none()
                ).right()
            }

            is CoopInternalMessages.UserInputMessage.StartAdvertisingCoop -> negotiatedBid.map {
                "Już jesteś we współpracy z ${it.first}".left()
            }.getOrElse {
                WaitingForCompany(
                    myId,
                    travelName,
                    none(),
                    true
                ).right()
            }

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelUser -> negotiatedBid.map { "Już jesteś we współpracy z ${it.first}".left() }
                .getOrElse {
                    if (coopMessage.proposeSender == myId && coopMessage.travelName == travelName) {
                        WaitingForCompany(
                            myId,
                            travelName,
                            coopMessage.proposeReceiver.toOption(),
                            false
                        ).right()
                    } else {
                        "Nie jesteś poprawnym nadawcą wiadomości $coopMessage".left()
                    }
                }

            is CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelSystem -> negotiatedBid.map { "Gracz $myId już jest we współpracy z ${it.first}".left() }
                .getOrElse {
                    if (coopMessage.proposeReceiver == myId) {
                        this.right()
                    } else {
                        "Gracz $myId nie jest poprawnym odbiorcą wiadomości $coopMessage".left()
                    }
                }

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelAckUser -> negotiatedBid.map { "Już jesteś we współpracy z ${it.first}".left() }
                .getOrElse {
                    if (coopMessage.proposeReceiver == myId) {
                        ResourcesDecide.ResourceNegotiatingFirstPassive(
                            myId,
                            coopMessage.proposeSender,
                            coopMessage.travelName,
                            travelName.toOption()
                        ).right()
                    } else {
                        "Gracz $myId nie jest poprawnym odbiorcą wiadomości $coopMessage".left()
                    }
                }

            is CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelAckSystem -> negotiatedBid.map {
                "Gracz $myId już jest we współpracy z ${it.first}".left()
            }
                .getOrElse { "Wiadomość $coopMessage nie powinna pojawić się w stanie zbierania zasobów samodzielnie".left() }

            is CoopInternalMessages.UserInputMessage.GatheringJoinPlanningUser -> if (coopMessage.joiningSender == myId) {
                WaitingForCompany(
                    myId,
                    travelName,
                    coopMessage.joiningReceiver.toOption(),
                    false
                ).right()
            } else {
                "Nie jesteś poprawnym nadawcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.GatheringJoinPlanningAckSystem -> negotiatedBid.map {
                "Gracz $myId już jest we współpracy z ${it.first}".left()
            }
                .getOrElse { "Wiadomość $coopMessage nie powinna pojawić się w stanie zbierania zasobów samodzielnie".left() }

            is CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem -> this.right()

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSystem -> negotiatedBid.map {
                this.right()
            }
                .getOrElse { "Wiadomość $coopMessage nie powinna pojawić się w stanie zbierania zasobów samodzielnie".left() }

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSingleSystem -> negotiatedBid.map {
                "Wiadomość $coopMessage nie powinna pojawić się w stanie zbierania zasobów we współpracy".left()
            }.getOrElse {
                this.right()
            }

            is CoopInternalMessages.UserInputMessage.StartPlannedTravel -> negotiatedBid.map {
                if (myId != it.second.travelerId) {
                    "$myId próbował podróżować do $travelName, lecz powinien to być ${it.second.travelerId}".left()
                } else if (coopMessage.travelName != travelName) {
                    "Miasto z wiadomości różni się od wiadomości ze stanu: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoPlanningState.right()
                }
            }.getOrElse {
                if (coopMessage.travelName != travelName) {
                    "Miasto z wiadomości różni się od wiadomości ze stanu: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoPlanningState.right()
                }
            }

            is CoopInternalMessages.SystemOutputMessage.StartPlannedTravel -> negotiatedBid.map {
                if (it.first != it.second.travelerId) {
                    "${it.first} próbował podróżować do $travelName, lecz powinien to być ${it.second.travelerId}".left()
                } else if (coopMessage.travelName != travelName) {
                    "Miasto z wiadomości różni się od wiadomości ze stanu: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoPlanningState.right()
                }
            }
                .getOrElse { "Informacja o zakończeniu współpracy nie powinna pojawić się w stanie zbierania zasobów samodzielnie".left() }

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoPlanningState.right()

            else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie zbierania zasobów".left()
        }

        override fun secondPlayer(): Option<PlayerId> = negotiatedBid.map { it.first }
        override fun travelName(): Option<TravelName> = travelName.toOption()
        override fun traveller(): PlayerId = negotiatedBid.map { it.second.travelerId }.getOrElse { myId }
    }

    @Serializable
    @SerialName("WaitingForCompany")
    data class WaitingForCompany(
        val myId: PlayerId,
        val travelName: TravelName,
        val secondSide: OptionS<PlayerId>,
        val isAdvertising: Boolean
    ) : CoopStates, TravelSet {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> NoPlanningState.right()

            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (coopMessage.myId == myId) {
                GatheringResources(myId, coopMessage.travelName, none()).right()
            } else {
                "Nie jesteś poprawnym nadawcą wiadomości $coopMessage".left()
            }

            CoopInternalMessages.UserInputMessage.StartAdvertisingCoop -> WaitingForCompany(
                myId,
                travelName,
                secondSide,
                true
            ).right()

            CoopInternalMessages.UserInputMessage.StopAdvertisingCoop -> secondSide.map {
                WaitingForCompany(
                    myId,
                    travelName,
                    secondSide,
                    false
                ).right()
            }.getOrElse {
                GatheringResources(
                    myId,
                    travelName,
                    none()
                ).right()
            }

            is CoopInternalMessages.UserInputMessage.SimpleJoinPlanningAckUser -> if (coopMessage.joiningReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.joiningSender,
                    travelName,
                    travelName.toOption()
                ).right()
            } else {
                "Nie jesteś poprawnym odbiorcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.SimpleJoinPlanningSystem -> this.right()

            is CoopInternalMessages.UserInputMessage.GatheringJoinPlanningUser -> if (coopMessage.joiningSender == myId) {
                WaitingForCompany(
                    myId,
                    travelName,
                    coopMessage.joiningReceiver.toOption(),
                    isAdvertising
                ).right()
            } else {
                "Nie jesteś poprawnym nadawcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.GatheringJoinPlanningSystem -> this.right()

            is CoopInternalMessages.UserInputMessage.GatheringJoinPlanningAckUser -> if (coopMessage.joiningReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.joiningSender,
                    travelName,
                    travelName.toOption()
                ).right()
            } else {
                "Nie jesteś poprawnym odbiorcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.GatheringJoinPlanningAckSystem -> if (coopMessage.joiningSender == myId) {
                secondSide.map {
                    if (it == coopMessage.joiningReceiver) {
                        ResourcesDecide.ResourceNegotiatingFirstActive(
                            myId,
                            coopMessage.joiningReceiver,
                            coopMessage.travelName,
                            travelName.toOption()
                        ).right()
                    } else {
                        "Gracz ${coopMessage.joiningReceiver} zaakceptował ofertę za późno".left()
                    }
                }.getOrElse { "Gracz $myId nie wysłał jeszcze żadnych ogłoszeń współpracy".left() }
            } else {
                "Gracz $myId nie jest poprawnym nadawcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelUser -> if (myId == coopMessage.proposeSender && travelName == coopMessage.travelName) {
                WaitingForCompany(
                    myId,
                    travelName,
                    coopMessage.proposeReceiver.toOption(),
                    isAdvertising
                ).right()
            } else {
                "Gracz $myId nie jest poprawnym nadawcą lub podróż $travelName jest zła we wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelSystem -> this.right()

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelAckUser -> if (coopMessage.proposeReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.proposeSender,
                    coopMessage.travelName,
                    travelName.toOption()
                ).right()
            } else {
                "Nie jesteś poprawnym odbiorcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelAckSystem -> if (coopMessage.proposeSender == myId && coopMessage.travelName == travelName) {
                secondSide.map {
                    if (it == coopMessage.proposeReceiver) {
                        ResourcesDecide.ResourceNegotiatingFirstActive(
                            myId,
                            coopMessage.proposeReceiver,
                            travelName,
                            travelName.toOption()
                        ).right()
                    } else {
                        "Gracz ${coopMessage.proposeReceiver} zaakceptował ofertę za późno".left()
                    }
                }.getOrElse { "Gracz $myId nie wysłał jeszcze żadnych ogłoszeń współpracy".left() }
            } else {
                "Gracz $myId nie jest poprawnym nadawcą lub podróż $travelName jest zła we wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ResourcesGatheredSystem -> this.right()

            is CoopInternalMessages.SystemOutputMessage.ResourcesUnGatheredSingleSystem -> this.right()

            is CoopInternalMessages.UserInputMessage.StartPlannedTravel -> if (coopMessage.myId == myId) {
                if (coopMessage.travelName != travelName) {
                    "Miasto z wiadomości różni się od wiadomości ze stanu: ${coopMessage.travelName} vs. $travelName".left()
                } else {
                    NoPlanningState.right()
                }
            } else {
                "Nie jesteś poprawnym nadawcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoPlanningState.right()

            else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie szukania współpracy".left()
        }

        override fun secondPlayer(): Option<PlayerId> = secondSide
        override fun travelName(): Option<TravelName> = travelName.toOption()
        override fun traveller(): PlayerId = myId
    }

    @Serializable
    @SerialName("WaitingForOwnerAnswer")
    data class WaitingForOwnerAnswer(
        val myId: PlayerId,
        val ownerId: PlayerId
    ) : CoopStates {
        override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
            is CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage -> NoPlanningState.right()

            is CoopInternalMessages.UserInputMessage.StartPlanning -> if (coopMessage.myId == myId) {
                GatheringResources(
                    myId,
                    coopMessage.travelName,
                    none()
                ).right()
            } else {
                "Nie jesteś poprawnym nadawcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.SimpleJoinPlanningUser -> if (coopMessage.joiningSender == myId) {
                WaitingForOwnerAnswer(myId, coopMessage.joiningReceiver).right()
            } else {
                "Nie jesteś poprawnym nadawcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.SimpleJoinPlanningAckSystem -> if (coopMessage.joiningSenderId == myId && coopMessage.joiningReceiverId == ownerId) {
                ResourcesDecide.ResourceNegotiatingFirstActive(myId, ownerId, coopMessage.travelName, none()).right()
            } else {
                "Gracz $myId nie jest poprawnym nadawcą lub gracz $ownerId nie jest poprawnym odbiorcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.SystemOutputMessage.ProposeOwnTravelSystem -> this.right()

            is CoopInternalMessages.UserInputMessage.ProposeOwnTravelAckUser -> if (coopMessage.proposeReceiver == myId) {
                ResourcesDecide.ResourceNegotiatingFirstPassive(
                    myId,
                    coopMessage.proposeSender,
                    coopMessage.travelName,
                    none()
                ).right()
            } else {
                "Nie jesteś poprawnym odbiorcą wiadomości $coopMessage".left()
            }

            is CoopInternalMessages.UserInputMessage.StartSimpleTravel -> this.right()

            is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoPlanningState.right()

            else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie oczekiwania na dołączenie".left()
        }

        override fun secondPlayer(): Option<PlayerId> = ownerId.toOption()
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
                is CoopInternalMessages.UserInputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideUser ->
                    ResourceNegotiatingPassive(
                        myId,
                        passiveSide,
                        travelName,
                        coopMessage.bid,
                        previousTravelName
                    ).right()

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoPlanningState.right()

                else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie FirstResourceNegotiating.Active".left()
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
                is CoopInternalMessages.UserInputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem ->
                    ResourceNegotiatingActive(myId, activeSide, travelName, coopMessage.bid, previousTravelName).right()

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoPlanningState.right()

                else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie FirstResourceNegotiating.Passive".left()
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
            val myBid: ResourcesDecideValues,
            val previousTravelName: OptionS<TravelName>
        ) : ResourcesDecide {
            override fun parseCommand(coopMessage: CoopInternalMessages): ErrorOr<CoopStates> = when (coopMessage) {
                is CoopInternalMessages.UserInputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.UserInputMessage.ResourcesDecideUser ->
                    ResourceNegotiatingPassive(
                        myId,
                        passiveSide,
                        travelName,
                        coopMessage.bid,
                        previousTravelName
                    ).right()

                is CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser ->
                    GatheringResources(myId, travelName, (passiveSide to coopMessage.bid).toOption()).right()

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoPlanningState.right()

                else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie ResourceNegotiating.Active".left()
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
                is CoopInternalMessages.UserInputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.CancelNegotiationAtAnyStage -> previousTravelName.map {
                    GatheringResources(
                        myId,
                        it,
                        none()
                    ).right()
                }.getOrElse {
                    NoPlanningState.right()
                }

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideSystem ->
                    ResourceNegotiatingActive(myId, activeSide, travelName, coopMessage.bid, previousTravelName).right()

                is CoopInternalMessages.SystemOutputMessage.ResourcesDecideAckSystem -> if (coopMessage.finishReceiver == myId && coopMessage.finishSender == activeSide) {
                    GatheringResources(myId, travelName, (activeSide to coopMessage.bid).toOption()).right()
                } else {
                    "Gracz $myId nie jest poprawnym odbiorcą lub gracz $activeSide nie jest poprawnym nadawcą wiadomości $coopMessage".left()
                }

                is CoopInternalMessages.UserInputMessage.ExitGameSession -> NoPlanningState.right()

                else -> "Wiadomość $coopMessage nie powinna pojawić się w stanie ResourceNegotiating.Passive".left()
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
