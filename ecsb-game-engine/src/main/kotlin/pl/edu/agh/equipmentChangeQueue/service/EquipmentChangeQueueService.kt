package pl.edu.agh.equipmentChangeQueue.service

import arrow.core.Either
import arrow.fx.coroutines.metered
import arrow.fx.coroutines.repeat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.equipmentChangeQueue.dao.EquipmentChangeQueueDao
import pl.edu.agh.game.domain.UpdatedResources
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

class EquipmentChangeQueueService(private val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>) {

    private val logger by LoggerDelegate()

    @OptIn(ExperimentalTime::class)
    suspend fun startEquipmentChangeQueueLoop() {
        flow { emit(1) }.repeat().metered(300.milliseconds).map {
            Either.catch {
                Transactor.dbQuery {
                    EquipmentChangeQueueDao.performEquipmentChanges()()
                }.map {
                    logger.info("Performed equipment changes on $it, sending notifications to equipment change queue")
                    it.forEach { (gameSessionId, playerId) ->
                        equipmentChangeProducer.sendMessage(
                            gameSessionId, playerId, EquipmentInternalMessage.EquipmentChangeDetected(
                                UpdatedResources.empty
                            )
                        )
                    }
                }
            }.mapLeft {
                logger.error("Error while performing equipment changes from queue", it)
            }
        }.collect()
    }
}