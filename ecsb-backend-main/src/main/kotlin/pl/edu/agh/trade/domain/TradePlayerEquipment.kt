package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

@Serializable
data class TradePlayerEquipment(
    val money: NonNegInt,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
) {
    fun toPlayerEquipment(): PlayerEquipment = PlayerEquipment(money, 0.nonNeg, resources)

    companion object {
        val empty: TradePlayerEquipment = TradePlayerEquipment(
            NonNegInt(0),
            NonEmptyMap(mapOf(GameResourceName("mock here") to NonNegInt(0)))
        )
    }
}
