package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt

@Serializable
data class TradeEquipment(val money: NonNegInt, val resources: NonEmptyMap<GameResourceName, NonNegInt>) {
    companion object {
        fun fromEquipment(value: PlayerEquipment): TradeEquipment = TradeEquipment(value.money, value.resources)
    }
}