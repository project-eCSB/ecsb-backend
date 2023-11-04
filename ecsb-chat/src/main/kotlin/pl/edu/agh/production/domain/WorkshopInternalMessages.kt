package pl.edu.agh.production.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.PosInt

@Serializable
sealed interface WorkshopInternalMessages {

    @Serializable
    data class WorkshopStart(val amount: PosInt) : WorkshopInternalMessages
}
