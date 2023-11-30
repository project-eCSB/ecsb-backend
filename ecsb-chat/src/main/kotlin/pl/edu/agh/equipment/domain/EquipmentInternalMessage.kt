package pl.edu.agh.equipment.domain

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import kotlinx.serialization.Serializable
import pl.edu.agh.game.domain.UpdatedTokens

@Serializable
sealed interface EquipmentInternalMessage {
    fun updatedTokens(): Option<UpdatedTokens> = none()

    @Serializable
    data class EquipmentChangeWithTokens(val updatedTokens: UpdatedTokens) : EquipmentInternalMessage {
        override fun updatedTokens(): Option<UpdatedTokens> = updatedTokens.some()
    }

    @Serializable
    data class EquipmentChangeAfterCoop(val updatedTokens: UpdatedTokens) : EquipmentInternalMessage {
        override fun updatedTokens(): Option<UpdatedTokens> = updatedTokens.some()
    }

    @Serializable
    object CheckEquipmentsForCoop : EquipmentInternalMessage

    @Serializable
    object TimeTokenRegenerated : EquipmentInternalMessage
}
