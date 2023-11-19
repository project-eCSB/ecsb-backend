package pl.edu.agh.coop.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.Percentile

@Serializable
data class ResourcesDecideValues(
    val travelerId: PlayerId,
    val moneyRatio: Percentile,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
)
