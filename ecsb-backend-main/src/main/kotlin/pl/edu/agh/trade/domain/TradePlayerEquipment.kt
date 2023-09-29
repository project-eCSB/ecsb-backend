package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.Money
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

@Serializable
data class TradePlayerEquipment(
    val money: Money,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
) {
    fun toPlayerEquipment(): PlayerEquipment = PlayerEquipment(money, 0.nonNeg, resources)

    companion object {
        fun fromEquipment(value: PlayerEquipment): TradePlayerEquipment =
            TradePlayerEquipment(value.money, value.resources)

        val empty: TradePlayerEquipment = TradePlayerEquipment(
            Money(0),
            NonEmptyMap(mapOf(GameResourceName("mock here") to NonNegInt(0)))
        )
    }
}
