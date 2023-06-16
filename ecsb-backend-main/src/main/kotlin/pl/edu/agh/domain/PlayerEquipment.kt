package pl.edu.agh.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.zip
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

        fun validatePositive(playerEquipment: PlayerEquipment): Either<String, Unit> = either {
            val (money, time, resources) = playerEquipment
            if (money < 0) {
                raise("money negative $money")
            }
            if (time < 0) {
                raise("time negative $time")
            }
            resources.forEach { (name, value) ->
                if (value < 0) {
                    raise("${name.value}  $value")
                }
            }
        }
    }

    operator fun minus(other: PlayerEquipment): PlayerEquipment {
        val resources =
            this.resources.associate { it.name to it.value }.zip(other.resources.associate { it.name to it.value })
                .mapValues { (_, valuesPair) ->
                    val (first, second) = valuesPair
                    first - second
                }.map { (key, value) -> GameResourceDto(key, value) }
        return PlayerEquipment(
            money = this.money - other.money,
            time = this.time - other.time,
            resources = resources
        )
    }
}
