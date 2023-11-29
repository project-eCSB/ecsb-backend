package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.assets.domain.AssetNumber
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.*
import pl.edu.agh.utils.PosInt.Companion.posIntWrapper

object GameSessionUserClassesTable : Table("GAME_SESSION_USER_CLASSES"),
    Domainable<Pair<GameClassName, GameClassResourceDto>> {
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val className: Column<GameClassName> = stringWrapper(GameClassName::value, ::GameClassName)("CLASS_NAME")
    val resourceName: Column<GameResourceName> =
        stringWrapper(GameResourceName::value, ::GameResourceName)("RESOURCE_NAME")
    val walkingAnimationIndex: Column<AssetNumber> =
        intWrapper(AssetNumber::value, ::AssetNumber)("WALKING_ANIMATION_INDEX")
    val resourceSpriteIndex: Column<AssetNumber> =
        intWrapper(AssetNumber::value, ::AssetNumber)("RESOURCE_SPRITE_INDEX")
    val maxProduction: Column<PosInt> = posIntWrapper("MAX_PRODUCTION")
    val unitPrice: Column<PosInt> = posIntWrapper("UNIT_PRICE")
    val buyoutPrice: Column<Long> = long("BUYOUT_PRICE")
    val regenTime: Column<TimestampMillis> = longWrapper(TimestampMillis::value, ::TimestampMillis)("REGEN_TIME")

    override fun toDomain(resultRow: ResultRow): Pair<GameClassName, GameClassResourceDto> =
        resultRow[className] to GameClassResourceDto(
            resultRow[walkingAnimationIndex],
            resultRow[resourceName],
            resultRow[resourceSpriteIndex],
            resultRow[maxProduction],
            resultRow[unitPrice],
            resultRow[regenTime],
            resultRow[buyoutPrice]
        )

    override val domainColumns: List<Expression<*>> = listOf(
        className,
        walkingAnimationIndex,
        resourceName,
        resourceSpriteIndex,
        maxProduction,
        unitPrice,
        regenTime,
        buyoutPrice
    )
}
