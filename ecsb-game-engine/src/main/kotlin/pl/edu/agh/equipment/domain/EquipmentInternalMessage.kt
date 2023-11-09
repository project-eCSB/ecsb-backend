package pl.edu.agh.equipment.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.game.domain.UpdatedResources

@Serializable
sealed interface EquipmentInternalMessage {
    @Serializable
    data class EquipmentChangeDetected(val updatedResources: UpdatedResources) : EquipmentInternalMessage
}
