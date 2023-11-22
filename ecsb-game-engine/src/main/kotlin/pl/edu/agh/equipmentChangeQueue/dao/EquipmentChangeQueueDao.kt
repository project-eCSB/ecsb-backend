package pl.edu.agh.equipmentChangeQueue.dao

import arrow.core.*
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipmentChangeQueue.domain.EquipmentChangeQueueId
import pl.edu.agh.equipmentChangeQueue.domain.PlayerEquipmentAdditions
import pl.edu.agh.equipmentChangeQueue.table.EquipmentChangeQueueResourceItemTable
import pl.edu.agh.equipmentChangeQueue.table.EquipmentChangeQueueTable
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.*
import java.time.Instant

object EquipmentChangeQueueDao {
    private val logger by LoggerDelegate()

    data class EquipmentChangeQueueResult(
        val gameSessionId: GameSessionId,
        val playerId: PlayerId,
        val equipmentChangeQueueId: EquipmentChangeQueueId,
        val context: String,
        val money: OptionS<Money>,
        val resources: OptionS<NonEmptyMap<GameResourceName, NonNegInt>>
    )

    fun performEquipmentChanges(): DB<Option<NonEmptyList<EquipmentChangeQueueResult>>> = {
        val equipmentChangesPerformedResult = """
            with ecq as (
                update EQUIPMENT_CHANGE_QUEUE ecq set DONE_AT = now()
                    where DONE_AT is null and CREATED_AT + (interval '1 millisecond' * WAIT_TIME) <= now()
                    returning ecq.game_session_id, ecq.money_addition, ecq.player_id, ecq.id, ecq.context)
            update game_user
            set money = money + ecq.money_addition
            from ecq
            where ecq.game_session_id = game_user.game_session_id
              and ecq.player_id = game_user.name
            returning game_user.game_session_id as game_session_id, game_user.name as player_id, ecq.ID as id, ecq.context as context, ecq.money_addition as money;
        """.trimIndent().execAndMap {
            val gameSessionId = it.getInt("game_session_id").let(::GameSessionId)
            val playerId = it.getString("player_id").let(::PlayerId)
            val equipmentChangeQueueId = it.getLong("id").let(::EquipmentChangeQueueId)
            val context = it.getString("context")
            val moneyAddition = it.getLong("money").let(::Money).toOption()
            EquipmentChangeQueueResult(gameSessionId, playerId, equipmentChangeQueueId, context, moneyAddition, none())
        }

        equipmentChangesPerformedResult.toNonEmptyListOrNone().map { equipmentChangesPerformedResultNel ->
            // Use with caution as this is not good for every use case. Here we have auto-generated ids so this won't be sql injection
            val equipmentChangeQueueRawIds =
                equipmentChangesPerformedResult.map { it.equipmentChangeQueueId.value }.joinToString(",")

            logger.info("Updating equipments for $equipmentChangeQueueRawIds")

            val resourcesValues =
                """with ecqri as (select ecq.player_id, ecq.game_session_id, ecq.id, ecqri.resource_name, ecqri.resource_value_addition
                   from equipment_change_queue_resource_item ecqri
                            inner join equipment_change_queue ecq on ecqri.equipment_change_queue_id = ecq.id
                   where ecq.id in ($equipmentChangeQueueRawIds))
                update player_resource pr
                set value = pr.value + ecqri.resource_value_addition
                from ecqri
                where ecqri.game_session_id = pr.game_session_id
                  and ecqri.PLAYER_ID = pr.player_id
                  and ecqri.RESOURCE_NAME = pr.resource_name
                returning pr.game_session_id, pr.player_id, pr.resource_name, ecqri.resource_value_addition as resource_addition;
            """.trimIndent().execAndMap {
                    val gameSessionId = it.getInt("game_session_id").let(::GameSessionId)
                    val playerId = it.getString("player_id").let(::PlayerId)
                    val gameResourceName = it.getString("resource_name").let(::GameResourceName)
                    val resourceAddition =
                        it.getInt("resource_addition").let(::NonNegInt).toOption().filter { it.value != 0 }
                    (gameSessionId to playerId) to (gameResourceName to resourceAddition)
                }.groupBy({ (key, _) ->
                    key
                }, { (_, value) ->
                    value
                }).mapValues { (_, value) ->
                    value.map { (resourceName, resourceValue) -> resourceValue.map { resourceName to it } }
                        .filterOption()
                        .toNonEmptyMapOrNone()
                }.filterOption()

            equipmentChangesPerformedResultNel.map {
                it.copy(resources = resourcesValues[it.gameSessionId to it.playerId].toOption())
            }
        }
    }

    fun addItemToProcessing(
        waitTime: TimestampMillis,
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        playerEquipmentAdditions: PlayerEquipmentAdditions,
        context: String
    ): DB<Unit> = {
        val queueId = EquipmentChangeQueueTable.insert {
            it[EquipmentChangeQueueTable.gameSessionId] = gameSessionId
            it[EquipmentChangeQueueTable.playerId] = playerId
            it[EquipmentChangeQueueTable.moneyAddition] = playerEquipmentAdditions.money
            it[EquipmentChangeQueueTable.waitTime] = waitTime
            it[EquipmentChangeQueueTable.createdAt] = Instant.now()
            it[EquipmentChangeQueueTable.context] = context
        }[EquipmentChangeQueueTable.id]
        playerEquipmentAdditions.resources.map { resources ->
            EquipmentChangeQueueResourceItemTable.batchInsert(resources.toList()) { (resourceName, resourceValue) ->
                this[EquipmentChangeQueueResourceItemTable.equipmentChangeQueueId] = queueId
                this[EquipmentChangeQueueResourceItemTable.resourceName] = resourceName
                this[EquipmentChangeQueueResourceItemTable.resourceValueAddition] = resourceValue
            }
        }
    }
}
