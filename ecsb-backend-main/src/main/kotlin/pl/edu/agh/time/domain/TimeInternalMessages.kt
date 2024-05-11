package pl.edu.agh.time.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TimeInternalMessages {

    @Serializable
    @SerialName("internal/time/sync")
    object GameTimeSyncMessage : TimeInternalMessages
}
