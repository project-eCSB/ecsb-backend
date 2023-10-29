package pl.edu.agh.equipmentChangeQueue.domain

import arrow.core.Option
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.Money
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt

data class PlayerEquipmentAdditions(
    val money: Money,
    val resources: Option<NonEmptyMap<GameResourceName, NonNegInt>>
)
