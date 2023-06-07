package pl.edu.agh.messages.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.NonEmptySetS
import pl.edu.agh.utils.OptionS

@Serializable
data class MessageWrapper<T>(
    val message: T,
    val senderId: PlayerId,
    val gameSessionId: GameSessionId,
    val sendTo: OptionS<NonEmptySetS<PlayerId>>
)
