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
            TradeInternalMessages.UserInputMessage.CancelTradeUser -> ({ _: PlayerId -> "Could not cancel trade because you're not in trade" }).left()
            TradeInternalMessages.SystemInputMessage.CancelTradeSystem -> ({ _: PlayerId -> "System could not cancel trade because you're not in trade" }).left()
            is TradeInternalMessages.UserInputMessage.FindTradeUser -> WaitingForEager(
                tradeMessage.myId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.UserInputMessage.FindTradeAckUser -> TradeBidActive(
                tradeMessage.bidSenderId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.SystemInputMessage.FindTradeAckSystem -> TradeBidPassive(
                tradeMessage.bidAccepterId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.UserInputMessage.ProposeTradeUser -> WaitingForLastProposal(
                tradeMessage.myId,
                tradeMessage.proposalReceiverId
            ).right()

            is TradeInternalMessages.UserInputMessage.ProposeTradeAckUser -> FirstBidPassive(tradeMessage.proposalSenderId).right()
            is TradeInternalMessages.SystemInputMessage.ProposeTradeSystem -> NoTradeState.right()
            else -> ({ _: PlayerId -> "Trade message not valid while in NoTradeState $tradeMessage" }).left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
    }

    @Serializable
    @SerialName("WaitingForEager")
    data class WaitingForEager(val myId: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> = when (tradeMessage) {
            TradeInternalMessages.UserInputMessage.CancelTradeUser -> NoTradeState.right()
            is TradeInternalMessages.UserInputMessage.ProposeTradeUser -> NoTradeState.right()
            is TradeInternalMessages.UserInputMessage.FindTradeUser -> WaitingForEager(
                tradeMessage.myId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.UserInputMessage.FindTradeAckUser -> if (tradeMessage.bidSenderId != myId) {
                TradeBidActive(
                    tradeMessage.bidSenderId,
                    tradeMessage.offer
                ).right()
            } else {
                { _: PlayerId -> "Cannot start trade with myself" }.left()
            }

            is TradeInternalMessages.SystemInputMessage.FindTradeAckSystem -> if (tradeMessage.offer == tradeBid) {
                TradeBidPassive(tradeMessage.bidAccepterId, tradeBid).right()
            } else {
                { _: PlayerId -> "Sent bid doesn't match to one you announcing" }.left()
            }

            else -> ({ _: PlayerId -> "Trade message not valid while in WaitingForEager $tradeMessage" }).left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
    }

    @Serializable
    @SerialName("FirstBidActive")
    data class FirstBidActive(val passiveSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeUser -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeSystem -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${passiveSide.value}, leave him alone" }).left()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${passiveSide.value}, leave him alone" }).left()
                is TradeInternalMessages.UserInputMessage.TradeBidUser -> if (passiveSide == tradeMessage.receiverId) {
                    TradeBidPassive(
                        tradeMessage.receiverId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    { _: PlayerId -> "Looks like I sent bid to someone wrong, it should have been ${passiveSide.value}" }.left()
                }

                else -> ({ _: PlayerId -> "Trade message not valid while in FirstBid.Active $tradeMessage" }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.some()
    }

    @Serializable
    @SerialName("FirstBidPassive")
    data class FirstBidPassive(val activeSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeUser -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeSystem -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${activeSide.value}, leave him alone" }).left()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${activeSide.value}, leave him alone" }).left()
                is TradeInternalMessages.SystemInputMessage.TradeBidSystem -> if (tradeMessage.senderId == activeSide) {
                    TradeBidActive(
                        tradeMessage.senderId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    { myId: PlayerId -> "${myId.value} is trading with ${activeSide.value}, not you" }.left()
                }

                else -> ({ _: PlayerId -> "Trade message not valid while in FirstBid.Passive $tradeMessage" }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.some()
    }

    @Serializable
    @SerialName("TradeBidActive")
    data class TradeBidActive(val passiveSide: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeUser -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeSystem -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${passiveSide.value}, leave him alone" }).left()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${passiveSide.value}, leave him alone" }).left()
                is TradeInternalMessages.UserInputMessage.TradeBidUser -> if (tradeMessage.receiverId == passiveSide) {
                    TradeBidPassive(
                        tradeMessage.receiverId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    { _: PlayerId -> "Looks like I sent bid to ${tradeMessage.receiverId.value}, it should have been ${passiveSide.value}" }.left()
                }

                is TradeInternalMessages.UserInputMessage.TradeBidAckUser -> if (tradeMessage.receiverId == passiveSide) {
                    NoTradeState.right()
                } else {
                    { _: PlayerId -> "Looks like I accepted bid to ${tradeMessage.receiverId.value}, it should have been ${passiveSide.value}" }.left()
                }

                else -> ({ _: PlayerId -> "Trade message not valid while in TradeBid.Active $tradeMessage" }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.some()
    }

    @Serializable
    @SerialName("TradeBidPassive")
    data class TradeBidPassive(val activeSide: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeUser -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeSystem -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${activeSide.value}, leave him alone" }).left()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem -> ({ myId: PlayerId -> "${myId.value} is in trade with ${activeSide.value}, leave him alone" }).left()
                is TradeInternalMessages.SystemInputMessage.TradeBidSystem -> if (tradeMessage.senderId == activeSide) {
                    TradeBidActive(
                        tradeMessage.senderId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    { myId: PlayerId -> "Looks like ${tradeMessage.senderId.value} sent a bid to ${myId.value}, but it should have been ${activeSide.value}" }.left()
                }

                is TradeInternalMessages.SystemInputMessage.TradeBidAckSystem -> if (tradeMessage.senderId == activeSide) {
                    NoTradeState.right()
                } else {
                    { myId: PlayerId -> "Looks like ${tradeMessage.senderId.value} accept a bid to ${myId.value}, but it should have been ${activeSide.value}" }.left()
                }

                else -> ({ _: PlayerId -> "Trade message not valid while in TradeBid.Passive $tradeMessage" }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.some()
    }

    @Serializable
    @SerialName("WaitingForLastProposal")
    data class WaitingForLastProposal(val myId: PlayerId, val proposalReceiver: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeUser -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeSystem -> NoTradeState.right()
                is TradeInternalMessages.UserInputMessage.ProposeTradeUser -> WaitingForLastProposal(
                    tradeMessage.myId,
                    tradeMessage.proposalReceiverId
                ).right()

                is TradeInternalMessages.SystemInputMessage.ProposeTradeSystem -> WaitingForLastProposal(
                    myId,
                    proposalReceiver
                ).right()

                is TradeInternalMessages.UserInputMessage.ProposeTradeAckUser -> if (tradeMessage.proposalSenderId != myId) {
                    FirstBidPassive(tradeMessage.proposalSenderId).right()
                } else {
                    { _: PlayerId -> "Cannot start trade with myself" }.left()
                }

                is TradeInternalMessages.SystemInputMessage.ProposeTradeAckSystem -> if (tradeMessage.proposalReceiverId == proposalReceiver) {
                    FirstBidActive(tradeMessage.proposalReceiverId).right()
                } else {
                    { myId: PlayerId -> "I'm too late, ${myId.value} has already proposed ${proposalReceiver.value}" }.left()
                }

                is TradeInternalMessages.UserInputMessage.FindTradeUser -> WaitingForEager(
                    tradeMessage.myId,
                    tradeMessage.offer
                ).right()

                is TradeInternalMessages.UserInputMessage.FindTradeAckUser -> if (tradeMessage.bidSenderId != myId) {
                    TradeBidActive(
                        tradeMessage.bidSenderId,
                        tradeMessage.offer
                    ).right()
                } else {
                    { _: PlayerId -> "Cannot start trade with myself" }.left()
                }

                else -> ({ _: PlayerId -> "Trade message not valid while in WaitingForLastProposal $tradeMessage" }).left()
            }

        override fun secondPlayer(): Option<PlayerId> = proposalReceiver.some()
    }
}
