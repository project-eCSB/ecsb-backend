package pl.edu.agh.equipment.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import io.ktor.http.*
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipmentView
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor

sealed class SharedEquipmentException {
    abstract fun toResponse(): Pair<HttpStatusCode, String>

    data class ResourceNotFound(
        val gameSessionId: GameSessionId,
        val playerId: PlayerId,
        val gameResourceName: GameResourceName
    ) : SharedEquipmentException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.BadRequest to "Resource $gameResourceName not found for player $playerId in game $gameSessionId"
    }

    data class IncreaseUnavailable(
        val gameSessionId: GameSessionId,
        val playerId: PlayerId,
        val gameResourceName: GameResourceName,
        val maxCurrentQuantity: Int
    ) : SharedEquipmentException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.NotAcceptable to "Player $playerId in game $gameSessionId already shared maximum $gameResourceName quantity($maxCurrentQuantity)"
    }

    data class DecreaseUnavailable(
        val gameSessionId: GameSessionId,
        val playerId: PlayerId,
        val gameResourceName: GameResourceName
    ) : SharedEquipmentException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.NotAcceptable to "Player $playerId in game $gameSessionId cannot share negative value of $gameResourceName"
    }
}

interface EquipmentService {
    suspend fun getGameUserEquipment(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<PlayerEquipmentView>

    suspend fun increaseSharedResource(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameResourceName: GameResourceName
    ): Either<SharedEquipmentException, Unit>

    suspend fun decreaseSharedResource(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameResourceName: GameResourceName
    ): Either<SharedEquipmentException, Unit>
}

class EquipmentServiceImpl : EquipmentService {
    private val logger by LoggerDelegate()

    override suspend fun getGameUserEquipment(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<PlayerEquipmentView> = Transactor.dbQuery {
        logger.info("Fetching equipment of user $playerId in game session $gameSessionId")
        PlayerResourceDao.getUserEquipment(gameSessionId, playerId)
    }

    override suspend fun increaseSharedResource(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameResourceName: GameResourceName
    ): Either<SharedEquipmentException, Unit> =
        either {
            Transactor.dbQuery {
                validateResources(gameSessionId, playerId, gameResourceName, true).bind()
                PlayerResourceDao.changeSharedResource(gameSessionId, playerId, gameResourceName, true)
            }
        }

    override suspend fun decreaseSharedResource(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameResourceName: GameResourceName
    ): Either<SharedEquipmentException, Unit> =
        either {
            Transactor.dbQuery {
                validateResources(gameSessionId, playerId, gameResourceName, false).bind()
                PlayerResourceDao.changeSharedResource(gameSessionId, playerId, gameResourceName, false)
            }
        }

    private fun validateResources(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameResourceName: GameResourceName,
        increase: Boolean

    ): Either<SharedEquipmentException, Unit> = either {
        val (value, sharedValue) = PlayerResourceDao.getPlayerResourceValues(
            gameSessionId,
            playerId,
            gameResourceName
        ).toEither { SharedEquipmentException.ResourceNotFound(gameSessionId, playerId, gameResourceName) }.bind()

        if (increase) {
            if (sharedValue.value == value.value) {
                raise(
                    SharedEquipmentException.IncreaseUnavailable(
                        gameSessionId,
                        playerId,
                        gameResourceName,
                        value.value
                    )
                )
            }
        } else {
            if (sharedValue.value < 1) {
                raise(SharedEquipmentException.DecreaseUnavailable(gameSessionId, playerId, gameResourceName))
            }
        }
    }
}
