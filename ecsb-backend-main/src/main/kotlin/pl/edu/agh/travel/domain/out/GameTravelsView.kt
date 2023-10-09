package pl.edu.agh.travel.domain.out

import arrow.core.*
import kotlinx.serialization.Serializable
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.*

@Serializable
data class GameTravelsView(
    val name: TravelName,
    val time: OptionS<PosInt>,
    val moneyRange: Range<PosInt>,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
) {
    fun diff(maybeResourcesDecideValues: ResourcesDecideValues): Option<ResourcesDecideValues> =
        maybeResourcesDecideValues.let { (goerId, money, resourcesWanted) ->
            resources.padZip(resourcesWanted).map { (resourceName, values) ->
                val (maybeNeeded, maybeWanted) = values

                val needed = maybeNeeded?.value ?: 0
                val wanted = maybeWanted?.value ?: 0

                if (needed - wanted > 0) {
                    (resourceName to NonNegInt(needed - wanted)).some()
                } else {
                    none()
                }
            }
                .filterOption()
                .toNonEmptyMapOrNone()
                .map {
                    Triple(goerId, money, it)
                }
        }

    companion object {
        fun create(
            name: TravelName,
            time: Option<PosInt>,
            moneyRange: Range<PosInt>
        ): (NonEmptyMap<GameResourceName, NonNegInt>) -> GameTravelsView = { resources ->
            GameTravelsView(name, time, moneyRange, resources)
        }
    }
}
