package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.ResourceId
import pl.edu.agh.utils.intWrapper

object GameSessionResourceTable : Table("GAME_SESSION_RESOURCE") {
    val id: Column<ResourceId> = intWrapper(ResourceId::value, ::ResourceId)("ID").autoIncrement()
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val resourceName: Column<String> = varchar("RESOURCE_NAME", 255)
}
