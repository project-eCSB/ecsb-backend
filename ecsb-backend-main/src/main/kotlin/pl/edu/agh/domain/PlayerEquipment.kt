package pl.edu.agh.domain

import arrow.core.zip
import kotlinx.serialization.Serializable

@Serializable
data class PlayerEquipment(
    val money: Int,
    val time: Int,
    val products: Map<ResourceId, Int>
) {
    companion object {
        fun getEquipmentChanges(equipment1: PlayerEquipment, equipment2: PlayerEquipment): PlayerEquipment {
            val money = equipment1.money - equipment2.money
            val time = equipment1.time - equipment2.time
            val resources = equipment1.products.zip(equipment2.products)
                .map { (resource, pair) ->
                    val (value1, value2) = pair
                    resource to (value1 - value2)
                }.toMap()
            return PlayerEquipment(money, time, resources)
        }

        fun getInverse(equipment: PlayerEquipment): PlayerEquipment {
            val money = equipment.money * -1
            val time = equipment.time * -1
            val resources = equipment.products.map { (resource, value) -> resource to (value * (-1)) }.toMap()
            return PlayerEquipment(money, time, resources)
        }
    }
}
