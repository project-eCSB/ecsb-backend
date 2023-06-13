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

        fun getInverse(equipment: PlayerEquipment): PlayerEquipment {
            val money = equipment.money * -1
            val time = equipment.time * -1
            val resources = equipment.resources.map { (resource, value) -> GameResourceDto(resource, value * (-1)) }
            return PlayerEquipment(money, time, resources)
        }
    }
}
