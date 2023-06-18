package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.AssetNumber
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.PosInt.Companion.posIntWrapper
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object GameSessionUserClassesTable : Table("GAME_SESSION_USER_CLASSES") {
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

    fun toDomain(rs: ResultRow): Pair<GameClassName, GameClassResourceDto> =
        rs[className] to GameClassResourceDto(
            rs[walkingAnimationIndex],
            rs[resourceName],
            rs[resourceSpriteIndex],
            rs[maxProduction],
            rs[unitPrice]
        )
}
