package pl.edu.agh.coop.domain

import arrow.core.EitherNel
import arrow.core.NonEmptyList
import arrow.core.mapOrAccumulate
import arrow.core.raise.either
import arrow.core.zip
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.toNonEmptyMapUnsafe

@Serializable
data class CoopPlayerEquipment(val resources: NonEmptyMap<GameResourceName, AmountDiff<NonNegInt>>) {
    fun validate(): EitherNel<String, CoopPlayerEquipment> = either<NonEmptyList<String>, Unit> {
        resources.mapOrAccumulate { (resourceName, diff) ->
            diff.validate().mapLeft { it + resourceName.value }.bind()
        }.bind()
    }.map { this }

    companion object {
        fun invoke(
            actualResources: NonEmptyMap<GameResourceName, NonNegInt>,
            neededResources: NonEmptyMap<GameResourceName, NonNegInt>
        ): CoopPlayerEquipment {
            val resources = actualResources.zip(neededResources).map { (resourceName, amountDiff) ->
                resourceName to AmountDiff(amountDiff)
            }.toNonEmptyMapUnsafe()

            return CoopPlayerEquipment(resources)
        }
    }
}
