package pl.edu.agh.equipment.domain

import kotlinx.serialization.Serializable

@Serializable
sealed interface EquipmentInternalMessage {
    @Serializable
    object CheckEquipmentForTrade : EquipmentInternalMessage

    @Serializable
    object EquipmentDetected : EquipmentInternalMessage
}
