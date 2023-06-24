package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@Serializable
data class PlayerEquipmentView(val full: PlayerEquipment, val shared: PlayerEquipment)
