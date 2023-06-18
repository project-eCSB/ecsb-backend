package pl.edu.agh.chat.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.DateSerializer
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Serializable
data class Message(
    val senderId: PlayerId,
    val message: ChatMessageADT,
    @Serializable(DateSerializer::class)
    val sentAt: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
)
