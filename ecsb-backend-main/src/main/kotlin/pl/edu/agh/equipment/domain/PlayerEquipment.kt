package pl.edu.agh.equipment.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt

@Serializable
data class PlayerEquipment(
    val money: Money,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
) {
    companion object {
        val empty: PlayerEquipment = PlayerEquipment(
            Money(0),
            NonEmptyMap(mapOf(GameResourceName("mock here") to NonNegInt(0)))
        )
    }
}
