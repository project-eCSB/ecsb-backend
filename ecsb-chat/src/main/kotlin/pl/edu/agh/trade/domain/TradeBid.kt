package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable

@Serializable
data class TradeBid(val senderOffer: TradePlayerEquipment, val senderRequest: TradePlayerEquipment) {
    companion object {
        val empty = TradeBid(TradePlayerEquipment.empty, TradePlayerEquipment.empty)
    }
}
