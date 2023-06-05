package pl.edu.agh.game.service

import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.NonEmptyList
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.Effect
import arrow.core.raise.effect
import arrow.core.raise.either
import arrow.core.raise.option
import io.ktor.http.*
import pl.edu.agh.assets.dao.MapAssetDao
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.domain.*
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.game.dao.GameSessionUserClassesDao
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.domain.`in`.GameInitParameters
import pl.edu.agh.game.domain.`in`.GameJoinCodeRequest
import pl.edu.agh.game.domain.out.GameJoinResponse
import pl.edu.agh.game.domain.out.GameSessionView
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor

sealed class JoinGameException {
    abstract fun toResponse(): Pair<HttpStatusCode, String>

    data class WrongParameter(val gameCode: String) : JoinGameException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.BadRequest to "Game code $gameCode not valid"
    }

    data class UserAlreadyInGame(val gameCode: String, val loginUserId: LoginUserId) : JoinGameException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.Forbidden to "User ${loginUserId.id} already in game $gameCode"
    }
}

sealed class CreationException {
    abstract fun toResponse(): Pair<HttpStatusCode, String>

    data class UnknownError(val message: String) : CreationException() {
        override fun toResponse(): Pair<HttpStatusCode, String> = HttpStatusCode.InternalServerError to message
    }

    data class EmptyString(val emptyStringMessage: String) : CreationException() {
        override fun toResponse(): Pair<HttpStatusCode, String> = HttpStatusCode.BadRequest to emptyStringMessage
    }

    class MapNotFound(val message: String) : CreationException() {
        override fun toResponse(): Pair<HttpStatusCode, String> = HttpStatusCode.BadRequest to message
    }

    class DataNotValid(val message: String) : CreationException() {
        override fun toResponse(): Pair<HttpStatusCode, String> = HttpStatusCode.BadRequest to message
    }
}

class GameServiceImpl(
    private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>,
    private val gameAuthService: GameAuthService
) : GameService {
    private val logger by LoggerDelegate()

    override suspend fun createGame(
        gameInitParameters: GameInitParameters,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId> =
        Transactor.dbQueryEffect<CreationException, GameSessionId>(CreationException.UnknownError("Unknown exception happened during creating your game")) {
            either {
                logger.info("Trying to create game from $gameInitParameters")

                (
                    if (gameInitParameters.gameName.isNotBlank()) {
                        Right(Unit)
                    } else {
                        Left(CreationException.EmptyString("Game name cannot be empty"))
                    }
                    ).bind()

                val classes = gameInitParameters.classResourceRepresentation.keys
                val resources = gameInitParameters.classResourceRepresentation.map { it.value.gameResourceName }

                (
                    if (resources.toSet().size != resources.size) {
                        Left(CreationException.EmptyString("Resource name cannot be duplicated in one session"))
                    } else {
                        Right(Unit)
                    }
                    ).bind()

                val effectiveMapId = gameInitParameters.mapId.getOrElse { SavedAssetsId(0) }

                val mapAssetView = MapAssetDao.findMapConfig(effectiveMapId).toEither {
                    CreationException.MapNotFound(
                        "Map ${
                        gameInitParameters.mapId.map { it.value.toString() }.getOrElse { "default" }
                        } not found"
                    )
                }.bind()

                (
                    if (mapAssetView.mapAssetData.professionWorkshops.map { it.key }.toSet()
                        .intersect(classes.toSet()).size != classes.size
                    ) {
                        Left(CreationException.DataNotValid("Classes do not match with equivalent in map asset"))
                    } else {
                        Right(Unit)
                    }
                    ).bind()

                val createdGameSessionId: GameSessionId = GameSessionDao.createGameSession(
                    gameInitParameters.gameName,
                    effectiveMapId,
                    loginUserId
                )

                GameSessionUserClassesDao.upsertClasses(
                    gameInitParameters.classResourceRepresentation,
                    createdGameSessionId
                )

                logger.info("Game created with $gameInitParameters, its id is $createdGameSessionId")
                createdGameSessionId
            }
        }

    override suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSessionView> = Transactor.dbQuery {
        option {
            logger.info("Getting game session dto for $gameSessionId")
            val gameSessionDto: GameSessionDto = GameSessionDao.getGameSession(gameSessionId).bind()

            logger.info("Getting class representation list for $gameSessionId")
            val classRepresentation = GameSessionUserClassesDao.getClasses(gameSessionId).bind()

            GameSessionView(
                classRepresentation,
                gameSessionId,
                gameSessionDto.name,
                gameSessionDto.shortName,
                gameSessionDto.mapId
            )
        }
    }

    override suspend fun getGameUserStatus(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId
    ): Option<PlayerStatus> = Transactor.dbQuery {
        option {
            val playerStatus = GameUserDao.getGameUserInfo(loginUserId, gameSessionId).bind()
            val maybeCurrentPosition = redisHashMapConnector.findOne(gameSessionId, playerStatus.playerId)

            maybeCurrentPosition.fold({ playerStatus }, { playerPosition ->
                playerStatus.copy(
                    coords = playerPosition.coords,
                    direction = playerPosition.direction
                )
            })
        }
    }

    override suspend fun getGameUserEquipment(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId
    ): Option<PlayerEquipment> = Transactor.dbQuery {
        logger.info("Fetching equipment of user $loginUserId in game session $gameSessionId")
        PlayerResourceDao.getUserEquipmentByLoginUserId(gameSessionId, loginUserId)
    }

    override suspend fun joinToGame(
        gameJoinRequest: GameJoinCodeRequest,
        loginUserId: LoginUserId,
        userRoles: NonEmptyList<Role>
    ): Effect<JoinGameException, GameJoinResponse> = effect {
        Transactor.dbQuery {
            val gameSessionId = GameSessionDao.findGameSession(gameJoinRequest.gameCode)
                .toEither { JoinGameException.WrongParameter(gameJoinRequest.gameCode) }.bind()

            when (GameUserDao.getUserInGame(gameSessionId, loginUserId)) {
                is None -> Right(None)
                else -> Left(JoinGameException.UserAlreadyInGame(gameJoinRequest.gameCode, loginUserId))
            }.bind()

            val alreadyLoggedGameUser = GameUserDao.getGameUserInfo(loginUserId, gameSessionId)

            if (alreadyLoggedGameUser.isNone()) {
                val (className, usage) = GameUserDao.getClassUsages(gameSessionId).toList().minByOrNull { it.second }!!
                logger.info("Using $className for user $loginUserId in game $gameSessionId because it has $usage")

                GameUserDao.insertUser(loginUserId, gameSessionId, gameJoinRequest.playerId, className)

                PlayerResourceDao.insertUserResources(gameSessionId, gameJoinRequest.playerId)
            } else {
                GameUserDao.updateUserInGame(gameSessionId, loginUserId, true)
            }

            val token =
                gameAuthService.getGameUserToken(gameSessionId, loginUserId, gameJoinRequest.playerId, userRoles)

            GameJoinResponse(token, gameSessionId)
        }
    }

    override suspend fun updateUserInGame(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        inGame: Boolean
    ) = Transactor.dbQuery { GameUserDao.updateUserInGame(gameSessionId, loginUserId, false); Unit }
}
