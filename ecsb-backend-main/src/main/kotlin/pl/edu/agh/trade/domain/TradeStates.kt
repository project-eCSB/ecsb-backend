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
            is TradeInternalMessages.UserInputMessage.FindTrade -> WaitingForEager(tradeMessage.offer).right()
            is TradeInternalMessages.UserInputMessage.FindTradeAck -> TradeBidActive(
                tradeMessage.bidSenderId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.SystemInputMessage.FindTradeAck -> TradeBidPassive(
                tradeMessage.bidAccepterId,
                tradeMessage.offer
            ).right()

            is TradeInternalMessages.UserInputMessage.ProposeTrade -> WaitingForLastProposal(tradeMessage.proposalReceiverId).right()
            is TradeInternalMessages.UserInputMessage.ProposeTradeAck -> FirstBidPassive(tradeMessage.proposalSenderId).right()
            is TradeInternalMessages.SystemInputMessage.ProposeTrade -> NoTradeState.right()
            is TradeInternalMessages.SystemInputMessage.ProposeTradeAck -> FirstBidActive(tradeMessage.proposalReceiverId).right()
            else -> "Trade message not valid while in NoTradeState $tradeMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
    }

    @Serializable
    @SerialName("WaitingForEager")
    data class WaitingForEager(val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> = when (tradeMessage) {
            TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
            TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
            is TradeInternalMessages.UserInputMessage.ProposeTrade -> NoTradeState.right()
            is TradeInternalMessages.UserInputMessage.FindTrade -> WaitingForEager(tradeMessage.offer).right()
            is TradeInternalMessages.UserInputMessage.FindTradeAck -> TradeBidActive(
                tradeMessage.bidSenderId,
                tradeMessage.offer
            ).right()
            is TradeInternalMessages.SystemInputMessage.FindTradeAck -> if (tradeMessage.offer == tradeBid) {
                TradeBidPassive(tradeMessage.bidAccepterId, tradeBid).right()
            } else {
                "Trade bids don't match".left()
            }
            else -> "Trade message not valid while in WaitingForEager $tradeMessage".left()
        }

        override fun secondPlayer(): Option<PlayerId> = none()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("FirstBidActive")
    data class FirstBidActive(val passiveSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.UserInputMessage.TradeBidInternal -> if (passiveSide == tradeMessage.receiverId) {
                    TradeBidPassive(
                        tradeMessage.receiverId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Sent bid to someone wrong".left()
                }

                else -> "Trade message not valid while in FirstBid.Active $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.toOption()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("FirstBidPassive")
    data class FirstBidPassive(val activeSide: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.TradeBidInternal -> if (tradeMessage.senderId == activeSide) {
                    TradeBidActive(
                        tradeMessage.senderId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Got message from someone else".left()
                }
                else -> "Trade message not valid while in FirstBid.Passive $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.toOption()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("TradeBidActive")
    data class TradeBidActive(val passiveSide: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.UserInputMessage.TradeBidAckInternal -> if (tradeMessage.receiverId == passiveSide) {
                    NoTradeState.right()
                } else {
                    "I accept bid to someone wrong".left()
                }
                is TradeInternalMessages.UserInputMessage.TradeBidInternal -> if (tradeMessage.receiverId == passiveSide) {
                    TradeBidPassive(
                        tradeMessage.receiverId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Sent bid to someone wrong".left()
                }
                else -> "Trade message not valid while in TradeBid.Active $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = passiveSide.toOption()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("TradeBidPassive")
    data class TradeBidPassive(val activeSide: PlayerId, val tradeBid: TradeBid) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.SystemInputMessage.TradeBidAckInternal -> if (tradeMessage.senderId == activeSide) {
                    NoTradeState.right()
                } else {
                    "Got bid accept from someone wrong".left()
                }
                is TradeInternalMessages.SystemInputMessage.TradeBidInternal -> if (tradeMessage.senderId == activeSide) {
                    TradeBidActive(
                        tradeMessage.senderId,
                        tradeMessage.tradeBid
                    ).right()
                } else {
                    "Got bid from someone wrong".left()
                }
                else -> "Trade message not valid while in TradeBid.Passive $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = activeSide.toOption()
        override fun busy(): Boolean = true
    }

    @Serializable
    @SerialName("WaitingForLastProposal")
    data class WaitingForLastProposal(val proposalReceiver: PlayerId) : TradeStates {
        override fun parseCommand(tradeMessage: TradeInternalMessages): ErrorOr<TradeStates> =
            when (tradeMessage) {
                TradeInternalMessages.UserInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                TradeInternalMessages.SystemInputMessage.CancelTradeAtAnyStage -> NoTradeState.right()
                is TradeInternalMessages.UserInputMessage.ProposeTrade -> WaitingForLastProposal(tradeMessage.proposalReceiverId).right()
                is TradeInternalMessages.SystemInputMessage.ProposeTrade -> WaitingForLastProposal(proposalReceiver).right()
                is TradeInternalMessages.UserInputMessage.ProposeTradeAck -> FirstBidPassive(tradeMessage.proposalSenderId).right()
                is TradeInternalMessages.SystemInputMessage.ProposeTradeAck -> if (tradeMessage.proposalReceiverId == proposalReceiver) {
                    FirstBidActive(tradeMessage.proposalReceiverId).right()
                } else {
                    "Someone wrong accepts trade with me".left()
                }

                is TradeInternalMessages.UserInputMessage.FindTrade -> WaitingForEager(tradeMessage.offer).right()
                is TradeInternalMessages.UserInputMessage.FindTradeAck -> TradeBidActive(
                    tradeMessage.bidSenderId,
                    tradeMessage.offer
                ).right()
                else -> "Trade message not valid while in WaitingForLastProposal $tradeMessage".left()
            }

        override fun secondPlayer(): Option<PlayerId> = proposalReceiver.toOption()
    }
}
