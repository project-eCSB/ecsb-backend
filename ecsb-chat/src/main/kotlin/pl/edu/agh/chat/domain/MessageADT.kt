package pl.edu.agh.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId


@Serializable
sealed class MessageADT {
    @Serializable
    @SerialName("unicast")
    data class UnicastMessage(val message: String, val sendTo: PlayerId) : MessageADT()

    @Serializable
    @SerialName("multicast")
    data class MulticastMessage(val message: String) : MessageADT()
}
