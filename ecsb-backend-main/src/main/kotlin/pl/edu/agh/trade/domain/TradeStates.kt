package pl.edu.agh.trade.domain

import arrow.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

typealias ErrorOr<T> = Either<String, T>

@Serializable
sealed interface TradeStates {
    fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates>
    fun secondPlayer(): Option<PlayerId>
    fun busy(): Boolean = false

    @Serializable
    @SerialName("NoTradeState")
    object NoTradeState : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> = when (tradeMessage) {
            TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
            TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
            is TradeInternalMessages.UserInputMessage.FindTrade -> WaitingForEager(
                tradeMessage.myId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.UserInputMessage.FindTradeAck -> TradeBidActive(
                tradeMessage.bidSenderId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.SystemInputMessage.FindTradeAck -> TradeBidPassive(
                tradeMessage.bidAccepterId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.UserInputMessage.ProposeTrade -> WaitingForLastProposal(
                tradeMessage.myId,
                tradeMessage.proposalReceiverId
            ).right()

            is TradeInternalMessages.UserInputMessage.ProposeTradeAck -> FirstBidPassive(tradeMessage.proposalSenderId).right()
            is TradeInternalMessages.SystemInputMessage.ProposeTrade -> NoTradeState.right()
            else -> "Trade message not valid while in NoTradeState $tradeMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
    }

    @Serializable
    @SerialName("WaitingForEager")
    data class WaitingForEager(val myId: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> = when (tradeMessage) {
            TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
            is TradeInternalMessages.UserInputMessage.ProposeTrade -> NoTradeState.right()
            is TradeInternalMessages.UserInputMessage.FindTrade -> WaitingForEager(
                tradeMessage.myId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.UserInputMessage.FindTradeAck -> if (tradeMessage.bidSenderId != myId) {
                TradeBidActive(
                    tradeMessage.bidSenderId,
                    tradeMessage.offer
                ).right()
            } else {
                "Cannot accept bid trade with myself $tradeMessage".left()
            }

            is TradeInternalMessages.SystemInputMessage.FindTradeAck -> if (tradeMessage.offer == tradeBid) {
                TradeBidPassive(tradeMessage.bidAccepterId, tradeBid).right()
            } else {
                "Trade bids don't match $tradeMessage".left()
            }

            else -> "Trade message not valid while in WaitingForEager $tradeMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
        override fun busy(): Boolean = false
    }

    @Serializable
    @SerialName("FirstBidActive")
    data class FirstBidActive(val passiveSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.UserInputMessage.TradeBidMsg -> if (passiveSide == tradeMessage.receiverId) {
                    TradeBidPassive(
                        tradeMessage.receiverId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Sent bid to someone wrong $tradeMessage".left()
                }

                else -> "Trade message not valid while in FirstBid.Active $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.some()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("FirstBidPassive")
    data class FirstBidPassive(val activeSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.TradeBidMsg -> if (tradeMessage.senderId == activeSide) {
                    TradeBidActive(
                        tradeMessage.senderId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Got message from someone else $tradeMessage".left()
                }

                else -> "Trade message not valid while in FirstBid.Passive $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.some()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("TradeBidActive")
    data class TradeBidActive(val passiveSide: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.UserInputMessage.TradeBidAck -> if (tradeMessage.receiverId == passiveSide) {
                    NoTradeState.right()
                } else {
                    "I accept bid to someone wrong $tradeMessage".left()
                }

                is TradeInternalMessages.UserInputMessage.TradeBidMsg -> if (tradeMessage.receiverId == passiveSide) {
                    TradeBidPassive(
                        tradeMessage.receiverId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Sent bid to someone wrong $tradeMessage".left()
                }

                else -> "Trade message not valid while in TradeBid.Active $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.some()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("TradeBidPassive")
    data class TradeBidPassive(val activeSide: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.TradeBidAck -> if (tradeMessage.senderId == activeSide) {
                    NoTradeState.right()
                } else {
                    "Got bid accept from someone wrong $tradeMessage".left()
                }

                is TradeInternalMessages.SystemInputMessage.TradeBidMsg -> if (tradeMessage.senderId == activeSide) {
                    TradeBidActive(
                        tradeMessage.senderId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Got bid from someone wrong $tradeMessage".left()
                }

                else -> "Trade message not valid while in TradeBid.Passive $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.some()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("WaitingForLastProposal")
    data class WaitingForLastProposal(val myId: PlayerId, val proposalReceiver: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.UserInputMessage.ProposeTrade -> WaitingForLastProposal(
                    tradeMessage.myId,
                    tradeMessage.proposalReceiverId
                ).right()
                is TradeInternalMessages.SystemInputMessage.ProposeTrade -> WaitingForLastProposal(myId, proposalReceiver).right()
                is TradeInternalMessages.UserInputMessage.ProposeTradeAck -> if (tradeMessage.proposalSenderId != myId) {
                    FirstBidPassive(tradeMessage.proposalSenderId).right()
                } else {
                    "Cannot accept trade with myself $tradeMessage".left()
                }
                is TradeInternalMessages.SystemInputMessage.ProposeTradeAck -> if (tradeMessage.proposalReceiverId == proposalReceiver) {
                    FirstBidActive(tradeMessage.proposalReceiverId).right()
                } else {
                    "Someone wrong accepts trade with me $tradeMessage".left()
                }

                is TradeInternalMessages.UserInputMessage.FindTrade -> WaitingForEager(
                    tradeMessage.myId,
                    tradeMessage.offer
                ).right()

                is TradeInternalMessages.UserInputMessage.FindTradeAck -> if (tradeMessage.bidSenderId != myId) {
                    TradeBidActive(
                        tradeMessage.bidSenderId,
                        tradeMessage.offer
                    ).right()
                } else {
                    "Cannot accept bid trade with myself $tradeMessage".left()
                }

                else -> "Trade message not valid while in WaitingForLastProposal $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = proposalReceiver.some()
    }
}
