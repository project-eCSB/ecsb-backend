package pl.edu.agh.equipment.domain

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.game.domain.UpdatedTokens

@Serializable
sealed interface EquipmentInternalMessage {
    fun updatedTokens(): Option<UpdatedTokens> = none()

    @Serializable
    @SerialName("internal/eq/change/tokens")
    data class EquipmentChangeWithTokens(val updatedTokens: UpdatedTokens) : EquipmentInternalMessage {
        override fun updatedTokens(): Option<UpdatedTokens> = updatedTokens.some()
    }

    @Serializable
    @SerialName("internal/eq/change/coop")
    data class EquipmentChangeAfterCoop(val updatedTokens: UpdatedTokens) : EquipmentInternalMessage {
        override fun updatedTokens(): Option<UpdatedTokens> = updatedTokens.some()
    }

    @Serializable
    @SerialName("internal/eq/check")
    object CheckEquipmentsForCoop : EquipmentInternalMessage

    @Serializable
    @SerialName("internal/eq/regenerated")
    object TimeTokenRegenerated : EquipmentInternalMessage
}
