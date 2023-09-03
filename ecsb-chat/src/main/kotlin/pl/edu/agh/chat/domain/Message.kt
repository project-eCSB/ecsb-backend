package pl.edu.agh.chat.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.DateSerializer
import java.time.LocalDateTime

@Serializable
data class Message(
    val senderId: PlayerId,
    val message: ChatMessageADT,
    @Serializable(DateSerializer::class)
    val sentAt: LocalDateTime = LocalDateTime.now()
)
