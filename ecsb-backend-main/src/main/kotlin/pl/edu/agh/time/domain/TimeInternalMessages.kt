package pl.edu.agh.time.domain

import kotlinx.serialization.Serializable

@Serializable
sealed interface TimeInternalMessages {

    @Serializable
    object GameTimeSyncMessage : TimeInternalMessages
}
