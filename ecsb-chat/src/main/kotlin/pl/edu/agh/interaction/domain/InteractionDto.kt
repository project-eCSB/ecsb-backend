package pl.edu.agh.interaction.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId

@Serializable
data class InteractionDto(
    val status: InteractionStatus,
    val otherPlayer: PlayerId
)
