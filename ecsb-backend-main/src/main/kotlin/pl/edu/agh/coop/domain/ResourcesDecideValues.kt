package pl.edu.agh.coop.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegFloat
import pl.edu.agh.utils.NonNegInt

@Serializable
data class ResourcesDecideValues(
    val playerId: PlayerId,
    val moneyRatio: NonNegFloat,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
)