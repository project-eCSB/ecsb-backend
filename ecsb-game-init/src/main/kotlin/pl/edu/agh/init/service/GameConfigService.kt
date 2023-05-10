package pl.edu.agh.init.service

import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.raise.Effect
import arrow.core.raise.effect
import arrow.core.raise.either
import arrow.core.raise.option
import arrow.core.right
import arrow.core.toNonEmptyListOrNone
import io.ktor.http.*
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.domain.*
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.game.dao.GameSessionUserClassesDao
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.table.GameSessionDto
import pl.edu.agh.init.domain.`in`.GameInitParameters
import pl.edu.agh.init.domain.`in`.GameJoinCodeRequest
import pl.edu.agh.init.domain.out.GameJoinResponse
import pl.edu.agh.init.domain.out.GameSessionView
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor

interface GameConfigService {
    suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSessionView>
    suspend fun getGameUserStatus(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<PlayerStatus>
    suspend fun joinToGame(
        gameJoinRequest: GameJoinCodeRequest,
        loginUserId: LoginUserId,
        userRoles: NonEmptyList<Role>
    ): Effect<JoinGameException, GameJoinResponse>

    suspend fun createGame(
        gameInitParameters: GameInitParameters,
        coords: Coordinates,
        direction: Direction,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId>
}

sealed class JoinGameException {
    abstract fun toResponse(): Pair<HttpStatusCode, String>

    data class WrongParameter(val gameCode: String) : JoinGameException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.BadRequest to "Game code $gameCode not valid"
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
}

class GameConfigServiceImpl(
    private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>,
    private val gameAuthService: GameAuthService
) :
    GameConfigService {
    private val logger by LoggerDelegate()

    override suspend fun createGame(
        gameInitParameters: GameInitParameters,
        coords: Coordinates,
        direction: Direction,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId> =
        Transactor.dbQueryEffect<CreationException, GameSessionId>(CreationException.UnknownError("Unknown exception happened during creating your game")) {
            either {
                logger.info("Trying to create game from $gameInitParameters")

                (
                    if (gameInitParameters.gameName.isNotBlank()) {
                        Right(1)
                    } else {
                        Left(CreationException.EmptyString("Game name cannot be empty"))
                    }
                    ).bind()

                (
                    if (gameInitParameters.charactersSpreadsheetUrl.isNotBlank()) {
                        Right(1)
                    } else {
                        Left(CreationException.EmptyString("Character spreadsheet url cannot be empty"))
                    }
                    ).bind()

                (
                    if (gameInitParameters.classRepresentation.isNotEmpty()) {
                        Right(1)
                    } else {
                        Left(CreationException.EmptyString("Class representation cannot be empty"))
                    }
                    ).bind()

                val createdGameSessionId: GameSessionId = GameSessionDao.createGameSession(
                    gameInitParameters.charactersSpreadsheetUrl,
                    gameInitParameters.gameName,
                    coords,
                    direction,
                    loginUserId
                ).right().bind()

                GameSessionUserClassesDao.upsertClasses(gameInitParameters.classRepresentation, createdGameSessionId)
                    .right().bind()

                logger.info("Game created with $gameInitParameters, its id is $createdGameSessionId")
                createdGameSessionId
            }
        }

    override suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSessionView> = Transactor.dbQuery {
        option {
            logger.info("Getting game session dto for $gameSessionId")
            val gameSessionDto: GameSessionDto = GameSessionDao.getGameSession(gameSessionId).bind()

            logger.info("Getting class representation list for $gameSessionId")
            val classRepresentationList =
                GameSessionUserClassesDao.getClasses(gameSessionId).toList().toNonEmptyListOrNone().bind()

            GameSessionView(
                classRepresentationList.toMap(),
                gameSessionDto.characterSpriteUrl,
                gameSessionId,
                gameSessionDto.name,
                gameSessionDto.shortName
            )
        }
    }

    override suspend fun getGameUserStatus(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId
    ): Option<PlayerStatus> =
        Transactor.dbQuery {
            option {
                val playerStatus = GameUserDao.getGameUserInfo(loginUserId, gameSessionId).bind()
                val maybeCurrentPosition = redisHashMapConnector.findOne(gameSessionId, playerStatus.playerId)

                maybeCurrentPosition.fold(
                    { playerStatus },
                    { playerPosition ->
                        playerStatus.copy(
                            coords = playerPosition.coords,
                            direction = playerPosition.direction
                        )
                    }
                )
            }
        }

    override suspend fun joinToGame(
        gameJoinRequest: GameJoinCodeRequest,
        loginUserId: LoginUserId,
        userRoles: NonEmptyList<Role>
    ): Effect<JoinGameException, GameJoinResponse> =
        effect {
            Transactor.dbQuery {
                val gameSessionId = GameSessionDao.findGameSession(gameJoinRequest.gameCode)
                    .toEither { JoinGameException.WrongParameter(gameJoinRequest.gameCode) }.bind()

                val alreadyLoggedGameUser = GameUserDao.getGameUserInfo(loginUserId, gameSessionId)

                if (alreadyLoggedGameUser.isNone()) {
                    val (className, usage) = GameUserDao.getClassUsages(gameSessionId).toList()
                        .minByOrNull { it.second }!!
                    logger.info("Using $className for user $loginUserId in game $gameSessionId because it has $usage")

                    GameUserDao.insertUser(loginUserId, gameSessionId, gameJoinRequest.playerId, className)
                        .right().bind()
                } else {
                    logger.info("User $loginUserId already added to game $gameSessionId before")
                }

                val token = gameAuthService.getGameUserToken(gameSessionId, loginUserId, userRoles)

                GameJoinResponse(token, gameSessionId)
            }
        }
}
