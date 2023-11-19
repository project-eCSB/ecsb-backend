package pl.edu.agh.equipmentChangeQueue.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipmentChangeQueue.domain.EquipmentChangeQueueId
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.*

object EquipmentChangeQueueTable : Table("EQUIPMENT_CHANGE_QUEUE") {
    val id: Column<EquipmentChangeQueueId> =
        longWrapper(EquipmentChangeQueueId::value, ::EquipmentChangeQueueId)("ID").autoIncrement()
    val gameSessionId = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val playerId = stringWrapper(PlayerId::value, ::PlayerId)("PLAYER_ID")
    val moneyAddition = longWrapper(Money::value, ::Money)("MONEY_ADDITION")
    val waitTime = longWrapper(TimestampMillis::value, ::TimestampMillis)("WAIT_TIME")
    val doneAt = timestampWithTimeZone("DONE_AT").nullable()
    val createdAt = timestampWithTimeZone("CREATED_AT")
    val context: Column<String> = varchar("CONTEXT", 255)
}
