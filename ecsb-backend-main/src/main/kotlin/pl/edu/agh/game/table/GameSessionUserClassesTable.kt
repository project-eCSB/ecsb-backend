package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.AssetNumber
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object GameSessionUserClassesTable : Table("GAME_SESSION_USER_CLASSES") {
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val name: Column<GameClassName> = stringWrapper(GameClassName::value, ::GameClassName)("NAME")
    val walkingAnimationIndex: Column<AssetNumber> =
        intWrapper(AssetNumber::value, ::AssetNumber)("WALKING_ANIMATION_INDEX")
}