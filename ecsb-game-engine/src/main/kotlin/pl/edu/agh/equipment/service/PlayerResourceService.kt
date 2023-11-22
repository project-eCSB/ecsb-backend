package pl.edu.agh.equipment.service

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.traverseEither
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.game.domain.UpdatedTokens
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.nonEmptyMapOf

class PlayerResourceService(private val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>) {

    suspend fun conductEquipmentChangeOnPlayer(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        playerEquipmentChanges: PlayerEquipmentChanges,
        messageF: (UpdatedTokens) -> EquipmentInternalMessage = EquipmentInternalMessage::EquipmentChangeWithTokens,
        parZipAction: suspend (suspend () -> Unit) -> Unit
    ): Either<NonEmptyList<String>, Unit> =
        conductEquipmentChangeOnPlayers(
            gameSessionId,
            nonEmptyMapOf(playerId to playerEquipmentChanges),
            messageF
        ) { action ->
            parZipAction(action)
        }.mapLeft { it.second }

    suspend fun conductEquipmentChangeOnPlayers(
        gameSessionId: GameSessionId,
        players: NonEmptyMap<PlayerId, PlayerEquipmentChanges>,
        messageF: (UpdatedTokens) -> EquipmentInternalMessage = EquipmentInternalMessage::EquipmentChangeWithTokens,
        parZipAction: suspend (suspend () -> Unit) -> Unit
    ): Either<Pair<PlayerId, NonEmptyList<String>>, Unit> = either {
        val updatedInfo = Transactor.dbQuery {
            players.toList().traverseEither { (playerId, equipmentChange) ->
                PlayerResourceDao.updateResources(gameSessionId, playerId, equipmentChange)()
                    .map(playerId::to)
                    .mapLeft(playerId::to)
            }
        }.bind()

        parZipAction {
            updatedInfo.forEach { (playerId, updatedResources) ->
                equipmentChangeProducer.sendMessage(
                    gameSessionId,
                    playerId,
                    messageF(updatedResources)
                )
            }
        }
    }
}
