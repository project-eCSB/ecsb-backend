package pl.edu.agh.equipmentChanges.domain

import kotlinx.serialization.Serializable

@Serializable
sealed interface EquipmentChangeADT {
    @Serializable
    object CheckEquipmentForTrade : EquipmentChangeADT

    @Serializable
    object EquipmentChangeDetected : EquipmentChangeADT
}
