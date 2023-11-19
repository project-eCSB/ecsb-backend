package pl.edu.agh.game.domain.requests

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.MapDataTypes.Travel
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.`in`.TravelParameters
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.PosInt

@Serializable
data class GameInitParameters(
    val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
    val gameName: String,
    val travels: NonEmptyMap<Travel, NonEmptyMap<TravelName, TravelParameters>>,
    val mapAssetId: OptionS<SavedAssetsId>,
    val tileAssetId: OptionS<SavedAssetsId>,
    val characterAssetId: OptionS<SavedAssetsId>,
    val resourceAssetsId: OptionS<SavedAssetsId>,
    val timeForGame: TimestampMillis,
    val maxTimeAmount: NonNegInt,
    val walkingSpeed: PosInt,
    val defaultMoney: Money,
    val interactionRadius: PosInt,
    val maxPlayerAmount: NonNegInt
)
