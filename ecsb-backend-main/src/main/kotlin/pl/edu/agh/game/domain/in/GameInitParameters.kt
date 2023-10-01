package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.MapDataTypes.Travel
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameClassName
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
    val timeForGame: TimestampMillis = TimestampMillis(0),
    val maxTimeAmount: NonNegInt = NonNegInt(0),
    val walkingSpeed: PosInt = PosInt(1)
)
