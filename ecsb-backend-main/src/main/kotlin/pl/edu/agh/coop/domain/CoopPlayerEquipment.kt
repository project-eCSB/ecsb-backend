package pl.edu.agh.coop.domain

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.toNonEmptyMapUnsafe

@Serializable
data class CoopPlayerEquipment(
    val money: ResourceDiff<NonNegInt>,
    val time: ResourceDiff<NonNegInt>,
    val resources: NonEmptyMap<GameResourceName, ResourceDiff<NonNegInt>>
) {
    fun validate(): EitherNel<String, CoopPlayerEquipment> = either<NonEmptyList<String>, Unit> {
        zipOrAccumulate(
            { money.validate().mapLeft { nonEmptyListOf(it + "money") } },
            { time.validate().mapLeft { nonEmptyListOf(it + "time") } },
            {
                resources.mapOrAccumulate<GameResourceName, String, ResourceDiff<NonNegInt>, Unit> { (resourceName, diff) ->
                    diff.validate().mapLeft { it + resourceName.value }
                }
            },
            { _, _, _ -> }
        )
    }.map { this }

    companion object {
        fun invoke(
            actualEquipment: PlayerEquipment,
            neededEquipment: PlayerEquipment
        ): CoopPlayerEquipment {
            val money = ResourceDiff(actualEquipment.money to neededEquipment.money)
            val time = ResourceDiff(actualEquipment.time to neededEquipment.time)
            val resources = actualEquipment.resources.zip(neededEquipment.resources).map { (actual, needed) ->
                actual to ResourceDiff(needed)
            }.toNonEmptyMapUnsafe()

            return CoopPlayerEquipment(money, time, resources)
        }
    }
}
