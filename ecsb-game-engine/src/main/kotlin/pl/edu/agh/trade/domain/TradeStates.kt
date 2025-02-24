package pl.edu.agh.trade.domain

import arrow.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

typealias ErrorOr<T> = Either<(PlayerId) -> String, T>

@Serializable
sealed interface TradeStates {
    fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates>
    fun secondPlayer(): Option<PlayerId>

    @Serializable
    @SerialName("NoTradeState")
    object NoTradeState : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> = when (tradeMessage) {
            is TradeInternalMessages.UserInputMessage.CancelTradeUser -> this.right()

            TradeInternalMessages.SystemOutputMessage.CancelTradeSystem -> this.right()

            is TradeInternalMessages.UserInputMessage.ProposeTradeUser ->
                WaitingForLastProposal(tradeMessage.myId, tradeMessage.proposalReceiverId).right()

            is TradeInternalMessages.UserInputMessage.ProposeTradeAckUser ->
                FirstBidPassive(tradeMessage.proposalSenderId).right()

            is TradeInternalMessages.SystemOutputMessage.ProposeTradeSystem -> this.right()

            else -> ({ _: PlayerId ->
                "Wiadomość $tradeMessage nie powinna pojawić się w stanie NoTradeState"
            }).left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
    }

    @Serializable
    @SerialName("WaitingForLastProposal")
    data class WaitingForLastProposal(val myId: PlayerId, val proposalReceiver: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                is TradeInternalMessages.UserInputMessage.CancelTradeUser ->
                    NoTradeState.right()

                TradeInternalMessages.SystemOutputMessage.CancelTradeSystem ->
                    NoTradeState.right()

                is TradeInternalMessages.UserInputMessage.ProposeTradeUser ->
                    WaitingForLastProposal(myId, tradeMessage.proposalReceiverId).right()

                is TradeInternalMessages.UserInputMessage.ProposeTradeAckUser ->
                    if (tradeMessage.proposalSenderId != myId) {
                        FirstBidPassive(tradeMessage.proposalSenderId).right()
                    } else {
                        { _: PlayerId -> "Cannot start trade with myself" }.left()
                    }

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeSystem -> this.right()

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeAckSystem ->
                    if (tradeMessage.proposalReceiverId == proposalReceiver) {
                        FirstBidActive(tradeMessage.proposalReceiverId).right()
                    } else {
                        { myId: PlayerId ->
                            "I'm too late, ${myId.value} has already proposed ${proposalReceiver.value}"
                        }.left()
                    }

                else -> ({ _: PlayerId ->
                    "Trade message not valid while in WaitingForLastProposal $tradeMessage"
                }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = proposalReceiver.some()
    }

    @Serializable
    @SerialName("FirstBidActive")
    data class FirstBidActive(val passiveSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                is TradeInternalMessages.UserInputMessage.CancelTradeUser ->
                    NoTradeState.right()

                TradeInternalMessages.SystemOutputMessage.CancelTradeSystem ->
                    NoTradeState.right()

                is TradeInternalMessages.SystemOutputMessage.TradeRemind -> if (tradeMessage.senderId == passiveSide) {
                    this.right()
                } else {
                    { myId: PlayerId ->
                        "Wygląda na to, że ${tradeMessage.senderId.value} wysłał pogonienie do ${myId.value}, gdy powinien wysłać ${passiveSide.value}"
                    }.left()
                }

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${passiveSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${passiveSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.UserInputMessage.TradeBidUser ->
                    if (passiveSide == tradeMessage.receiverId) {
                        TradeBidPassive(tradeMessage.receiverId).right()
                    } else {
                        { _: PlayerId ->
                            "Wygląda na to, że wysłałem ofertę ${tradeMessage.receiverId}, podczas gdy handluję z ${passiveSide.value}"
                        }.left()
                    }

                else -> ({ _: PlayerId ->
                    "Wiadomość $tradeMessage nie powinna pojawić się w stanie FirstBid.Active "
                }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.some()
    }

    @Serializable
    @SerialName("FirstBidPassive")
    data class FirstBidPassive(val activeSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                is TradeInternalMessages.UserInputMessage.CancelTradeUser ->
                    NoTradeState.right()

                TradeInternalMessages.SystemOutputMessage.CancelTradeSystem ->
                    NoTradeState.right()

                is TradeInternalMessages.UserInputMessage.TradeRemind -> if (tradeMessage.receiverId == activeSide) {
                    this.right()
                } else {
                    { _: PlayerId ->
                        "Wygląda na to, że wysłałem pogonienie do ${tradeMessage.receiverId.value}, gdy powinienem do ${activeSide.value}"
                    }.left()
                }

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${activeSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${activeSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.SystemOutputMessage.TradeBidSystem ->
                    if (tradeMessage.senderId == activeSide) {
                        TradeBidActive(tradeMessage.senderId).right()
                    } else {
                        { myId: PlayerId ->
                            "Wygląda na to, że ${tradeMessage.senderId.value} wysłał ofertę do ${myId.value}, gdy powinien wysłać  ${activeSide.value}"
                        }.left()
                    }

                else -> ({ _: PlayerId ->
                    "Wiadomość $tradeMessage nie powinna pojawić się w stanie FirstBid.Passive"
                }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.some()
    }

    @Serializable
    @SerialName("TradeBidActive")
    data class TradeBidActive(val passiveSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                is TradeInternalMessages.UserInputMessage.CancelTradeUser ->
                    NoTradeState.right()

                TradeInternalMessages.SystemOutputMessage.CancelTradeSystem ->
                    NoTradeState.right()

                is TradeInternalMessages.SystemOutputMessage.TradeRemind -> if (tradeMessage.senderId == passiveSide) {
                    this.right()
                } else {
                    { myId: PlayerId ->
                        "Wygląda na to, że ${tradeMessage.senderId.value} wysłał pogonienie do ${myId.value}, gdy powinien wysłać ${passiveSide.value}"
                    }.left()
                }

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${passiveSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${passiveSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.UserInputMessage.TradeBidUser ->
                    if (tradeMessage.receiverId == passiveSide) {
                        TradeBidPassive(tradeMessage.receiverId).right()
                    } else {
                        { _: PlayerId ->
                            "Wygląda na to, że wysłałem ofertę do ${tradeMessage.receiverId.value}, podczas gdy handluję z ${passiveSide.value}"
                        }.left()
                    }

                is TradeInternalMessages.UserInputMessage.TradeBidAckUser ->
                    if (tradeMessage.receiverId == passiveSide) {
                        NoTradeState.right()
                    } else {
                        { _: PlayerId ->
                            "Wygląda na to, że zaakceptowałem ofertę od ${tradeMessage.receiverId.value}, podczas gdy handluję z ${passiveSide.value}"
                        }.left()
                    }

                else -> ({ _: PlayerId ->
                    "Wiadomość $tradeMessage nie powinna pojawić się w stanie TradeBid.Active"
                }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.some()
    }

    @Serializable
    @SerialName("TradeBidPassive")
    data class TradeBidPassive(val activeSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                is TradeInternalMessages.UserInputMessage.CancelTradeUser ->
                    NoTradeState.right()

                TradeInternalMessages.SystemOutputMessage.CancelTradeSystem ->
                    NoTradeState.right()

                is TradeInternalMessages.UserInputMessage.TradeRemind -> if (tradeMessage.receiverId == activeSide) {
                    this.right()
                } else {
                    { _: PlayerId ->
                        "Wygląda na to, że wysłałem pogonienie do ${tradeMessage.receiverId.value}, gdy powinienem do ${activeSide.value}"
                    }.left()
                }

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${activeSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.SystemOutputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId ->
                    "${myId.value} handluje obecnie z ${activeSide.value}, musisz poczekać"
                }).left()

                is TradeInternalMessages.SystemOutputMessage.TradeBidSystem ->
                    if (tradeMessage.senderId == activeSide) {
                        TradeBidActive(tradeMessage.senderId).right()
                    } else {
                        { myId: PlayerId ->
                            "Wygląda na to, że ${tradeMessage.senderId.value} wysłał ofertę do ${myId.value}, podczas gdy handluje z ${activeSide.value}"
                        }.left()
                    }

                is TradeInternalMessages.SystemOutputMessage.TradeBidAckSystem ->
                    if (tradeMessage.senderId == activeSide) {
                        NoTradeState.right()
                    } else {
                        { myId: PlayerId ->
                            "Wygląda na to, że ${tradeMessage.senderId.value} zaakceptował ofertę ${myId.value}, podczas gdy handluje z ${activeSide.value}"
                        }.left()
                    }

                else -> ({ _: PlayerId ->
                    "Wiadomość $tradeMessage nie powinna pojawić się w stanie TradeBid.Passive"
                }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.some()
    }
}
