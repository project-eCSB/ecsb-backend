package pl.edu.agh.production.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.utils.PosInt

@Serializable
sealed interface WorkshopInternalMessages {

    @Serializable
    @SerialName("internal/workshop/start")
    data class WorkshopStart(val amount: PosInt) : WorkshopInternalMessages
}
