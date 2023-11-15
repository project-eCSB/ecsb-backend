package pl.edu.agh.equipment.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.game.domain.UpdatedTokens

@Serializable
sealed interface EquipmentInternalMessage {
    @Serializable
    data class EquipmentChangeWithTokens(val updatedTokens: UpdatedTokens) : EquipmentInternalMessage

    @Serializable
    object CheckEquipmentsForCoop : EquipmentInternalMessage
}
