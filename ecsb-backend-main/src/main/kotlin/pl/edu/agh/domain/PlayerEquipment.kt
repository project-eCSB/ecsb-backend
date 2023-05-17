package pl.edu.agh.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.game.domain.GameResourceDto

@Serializable
data class PlayerEquipment(
    val money: Int,
    val time: Int,
    val resources: List<GameResourceDto>
) {
    companion object {
        fun getEquipmentChanges(equipment1: PlayerEquipment, equipment2: PlayerEquipment): PlayerEquipment {
            val money = equipment1.money - equipment2.money
            val time = equipment1.time - equipment2.time
            val resources = equipment1.resources.zip(equipment2.resources)
                .map { (resource1, resource2) ->
                    GameResourceDto(resource1.name, resource1.value - resource2.value)
                }
            return PlayerEquipment(money, time, resources)
        }

        fun getInverse(equipment: PlayerEquipment): PlayerEquipment {
            val money = equipment.money * -1
            val time = equipment.time * -1
            val resources = equipment.resources.map { (resource, value) -> GameResourceDto(resource, value * (-1)) }
            return PlayerEquipment(money, time, resources)
        }
    }
}
