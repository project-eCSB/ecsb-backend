package pl.edu.agh.chat.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName

@Serializable
data class ProductionDto(val resourceName: GameResourceName, val quantity: Int)
