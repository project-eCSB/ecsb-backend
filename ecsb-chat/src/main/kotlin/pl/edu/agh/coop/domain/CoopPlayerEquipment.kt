package pl.edu.agh.coop.domain

import arrow.core.*
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.AmountDiff
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.toNonEmptyMapUnsafe

@Serializable
data class CoopPlayerEquipment(
    val resources: NonEmptyMap<GameResourceName, AmountDiff<NonNegInt>>,
    val timeTokensCoopInfo: OptionS<TimeTokensCoopInfo>
) {
    fun validate(): EitherNel<String, CoopPlayerEquipment> = either<NonEmptyList<String>, Unit> {
        resources.mapOrAccumulate { (resourceName, diff) ->
            diff.validate().mapLeft { it + resourceName.value }.bind()
        }.bind()

        timeTokensCoopInfo.map { timeTokensCoopInfo ->
            timeTokensCoopInfo.time.validate().mapLeft { nonEmptyListOf(it + "time") }
        }.getOrElse { Unit.right() }.bind()
    }.map { this }

    companion object {
        fun invoke(
            actualResources: NonEmptyMap<GameResourceName, NonNegInt>,
            neededResources: NonEmptyMap<GameResourceName, NonNegInt>,
            timeTokensCoopInfo: OptionS<TimeTokensCoopInfo>
        ): CoopPlayerEquipment {
            val resources = actualResources.zip(neededResources).map { (resourceName, amountDiff) ->
                resourceName to AmountDiff(amountDiff)
            }.toNonEmptyMapUnsafe()

            return CoopPlayerEquipment(resources, timeTokensCoopInfo)
        }
    }
}
