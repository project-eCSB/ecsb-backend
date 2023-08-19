package pl.edu.agh.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt

@Serializable
data class PlayerEquipment(
    val money: NonNegInt,
    val time: NonNegInt,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
) {
    companion object {
        val empty: PlayerEquipment = PlayerEquipment(
            NonNegInt(0),
            NonNegInt(0),
            NonEmptyMap(mapOf(GameResourceName("mock here") to NonNegInt(0)))
        )
    }
}
