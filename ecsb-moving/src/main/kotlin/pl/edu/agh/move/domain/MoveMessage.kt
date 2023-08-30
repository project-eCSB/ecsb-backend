package pl.edu.agh.move.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.DateSerializer
import java.time.LocalDateTime

@Serializable
data class MoveMessage(
    val senderData: PlayerId,
    val message: MessageADT,
    @Serializable(DateSerializer::class)
    val sentAt: LocalDateTime = LocalDateTime.now()
)
