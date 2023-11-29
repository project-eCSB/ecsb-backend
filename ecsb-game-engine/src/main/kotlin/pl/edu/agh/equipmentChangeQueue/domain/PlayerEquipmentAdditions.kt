package pl.edu.agh.equipmentChangeQueue.domain

import arrow.core.Option
import arrow.core.none
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt

data class PlayerEquipmentAdditions(
    val money: Money,
    val resources: Option<NonEmptyMap<GameResourceName, NonNegInt>> = none()
) {
    companion object {
        fun money(money: Money): PlayerEquipmentAdditions = PlayerEquipmentAdditions(money)
    }
}
