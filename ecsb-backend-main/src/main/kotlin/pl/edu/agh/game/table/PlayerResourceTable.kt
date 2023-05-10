package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.ResourceId
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object PlayerResourceTable : Table("PLAYER_RESOURCE") {
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val playerId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("PLAYER_ID")
    val resourceId: Column<ResourceId> = intWrapper(ResourceId::value, ::ResourceId)("RESOURCE_ID")
    val value: Column<Int> = integer("VALUE")
}
