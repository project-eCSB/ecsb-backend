package pl.edu.agh.game.service

import arrow.core.*
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.*
import io.ktor.http.*
import pl.edu.agh.assets.dao.MapAssetDao
import pl.edu.agh.assets.domain.GameAssets
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.game.dao.GameSessionUserClassesDao
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.game.domain.GameResults
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.domain.requests.GameInitParameters
import pl.edu.agh.game.domain.requests.GameJoinCodeRequest
import pl.edu.agh.game.domain.responses.GameJoinResponse
import pl.edu.agh.game.domain.responses.GameSessionView
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.`in`.GameTravelsInputDto
import pl.edu.agh.travel.domain.`in`.TravelParameters
import pl.edu.agh.utils.*
import pl.edu.agh.utils.Utils.flatTraverse

sealed class JoinGameException {
    abstract fun toResponse(): Pair<HttpStatusCode, String>

    data class WrongParameter(val gameCode: String) : JoinGameException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.BadRequest to "Game code $gameCode not valid"
    }

    data class UserAlreadyInGame(val gameCode: String, val loginUserId: LoginUserId) : JoinGameException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.Forbidden to "User ${loginUserId.value} already in game $gameCode"
    }

    data class DuplicatedPlayerId(val gameCode: String, val playerId: PlayerId) : JoinGameException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.Forbidden to "Nickname ${playerId.value} already in game $gameCode"
    }

    data class WrongPlayerId(
        val properPlayerId: PlayerId,
        val gameSessionId: GameSessionId
    ) : JoinGameException() {
        override fun toResponse(): Pair<HttpStatusCode, String> =
            HttpStatusCode.BadRequest to "You already registered to game $gameSessionId with name ${properPlayerId.value}"
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
    private val gameAuthService: GameAuthService,
    private val defaultAssets: GameAssets
) : GameService {
    private val logger by LoggerDelegate()

    override suspend fun copyGame(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        gameName: String
    ): Effect<CreationException, GameSessionId> = effect {
        val gameInfo =
            getGameInfo(gameSessionId).toEither { CreationException.DataNotValid("Game session not found") }.bind()

        val travels = gameInfo.travels.mapValues { (_, value) ->
            value.map { (_, travelInfo) ->
                travelInfo.name to TravelParameters(
                    travelInfo.resources,
                    travelInfo.moneyRange,
                    travelInfo.time
                )
            }.toNonEmptyMapUnsafe()
        }.toNonEmptyMapUnsafe()

        val gameInitParameters = GameInitParameters(
            classResourceRepresentation = gameInfo.classResourceRepresentation,
            gameName = gameName,
            travels = travels,
            mapAssetId = gameInfo.gameAssets.mapAssetId.some(),
            tileAssetId = gameInfo.gameAssets.tileAssetsId.some(),
            characterAssetId = gameInfo.gameAssets.characterAssetsId.some(),
            resourceAssetsId = gameInfo.gameAssets.resourceAssetsId.some(),
            timeForGame = gameInfo.timeForGame,
            maxTimeAmount = gameInfo.maxTimeAmount,
            defaultMoney = gameInfo.defaultMoney,
            walkingSpeed = gameInfo.walkingSpeed,
            interactionRadius = gameInfo.interactionRadius,
            maxPlayerAmount = gameInfo.maxPlayerAmount
        )

        createGame(gameInitParameters, loginUserId).bind()
    }

    override suspend fun getGameResults(gameSessionId: GameSessionId): Option<GameResults> = option {
        Transactor.dbQuery {
            val gameSessionName = GameSessionDao.getGameSessionNameAfterEnd(gameSessionId).bind()
            val playersLeaderBoard = GameUserDao.getUsersResults(gameSessionId)

            GameResults(gameSessionName, playersLeaderBoard)
        }
    }

    private fun createGameAssetsWithDefaults(
        gameInitParameters: GameInitParameters,
        defaultAssets: GameAssets
    ): GameAssets {
        val effectiveMapId = gameInitParameters.mapAssetId.getOrElse { defaultAssets.mapAssetId }
        val tileAssetId = gameInitParameters.tileAssetId.getOrElse { defaultAssets.tileAssetsId }
        val characterAssetId = gameInitParameters.characterAssetId.getOrElse { defaultAssets.characterAssetsId }
        val resourceAssetsId = gameInitParameters.resourceAssetsId.getOrElse { defaultAssets.resourceAssetsId }
        return GameAssets(
            mapAssetId = effectiveMapId,
            tileAssetsId = tileAssetId,
            characterAssetsId = characterAssetId,
            resourceAssetsId = resourceAssetsId
        )
    }

    override suspend fun createGame(
        gameInitParameters: GameInitParameters,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId> =
        Transactor.dbQueryEffect<CreationException, GameSessionId>(
            CreationException.UnknownError("Unknown exception happened during creating your game")
        ) {
            either {
                logger.info("Trying to create game from $gameInitParameters")

                ensure(gameInitParameters.gameName.isNotBlank()) {
                    CreationException.EmptyString("Game name cannot be empty")
                }

                val gameAssets: GameAssets = createGameAssetsWithDefaults(gameInitParameters, defaultAssets)

                val resources = gameInitParameters.classResourceRepresentation.map { it.value.gameResourceName }
                raiseWhen(resources.toSet().size != resources.size) {
                    CreationException.EmptyString("Resource name cannot be duplicated in one session")
                }

                val mapAssetDataDto = MapAssetDao.findMapConfig(gameAssets.mapAssetId).toEither {
                    CreationException.MapNotFound("Map ${gameAssets.mapAssetId} not found")
                }.bind()

                val classes = gameInitParameters.classResourceRepresentation.keys

                ensure(mapAssetDataDto.professionWorkshops.keys.intersect(classes).size == classes.size) {
                    CreationException.DataNotValid(
                        "Classes do not match with equivalent in map asset"
                    )
                }

                val createdGameSessionId =
                    GameSessionDao.createGameSession(
                        gameInitParameters.gameName,
                        gameAssets,
                        loginUserId,
                        gameInitParameters.timeForGame,
                        gameInitParameters.interactionRadius,
                        gameInitParameters.maxTimeAmount,
                        gameInitParameters.walkingSpeed,
                        gameInitParameters.defaultMoney,
                        gameInitParameters.maxPlayerAmount
                    )

                GameSessionUserClassesDao.upsertClasses(
                    gameInitParameters.classResourceRepresentation,
                    createdGameSessionId
                )

                upsertTravels(
                    createdGameSessionId,
                    gameInitParameters.travels
                ).bind()

                logger.info("Game created with $gameInitParameters, its id is $createdGameSessionId")
                createdGameSessionId
            }
        }

    private fun upsertTravels(
        createdGameSessionId: GameSessionId,
        travels: NonEmptyMap<MapDataTypes.Travel, NonEmptyMap<TravelName, TravelParameters>>
    ): Either<CreationException, List<Unit>> = either {
        val travelNames = travels.toList().flatMap { (_, travel) -> travel.map { (travelName, _) -> travelName } }

        ensure(travelNames.size == travelNames.toSet().size) {
            CreationException.DataNotValid("Duplicated travel names")
        }

        MapDataTypes.Travel.All.flatTraverse { travelType ->
            either {
                val travelsOfType = travels.getOrNone(travelType)
                    .toEither {
                        CreationException.DataNotValid(
                            "Travel ${travelType.dataValue} not valid because they don't exists"
                        )
                    }
                    .bind()
                val validatedTravels = travelsOfType.toList().traverse { (travelName, travelParameters) ->
                    either {
                        ensure(travelName.value.isNotBlank()) {
                            CreationException.DataNotValid("Travel name is blank")
                        }
                        GameTravelsInputDto(
                            createdGameSessionId,
                            travelType,
                            travelName,
                            travelParameters.time,
                            travelParameters.moneyRange
                        ) to travelParameters.assets
                    }
                }.bind()

                validatedTravels
            }
        }.bind()
            .map { (inputDto, assets) ->
                TravelDao.insertTravel(inputDto, assets)
            }
    }

    override suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSessionView> = Transactor.dbQuery {
        option {
            logger.info("Getting game session dto for $gameSessionId")
            val gameSessionDto: GameSessionDto = GameSessionDao.getGameSession(gameSessionId).bind()

            logger.info("Getting class representation list for $gameSessionId")
            val classRepresentation = GameSessionUserClassesDao.getClasses(gameSessionId).bind()

            logger.info("Getting travels list for $gameSessionId")
            val travels = TravelDao.getTravels(gameSessionId).bind()

            GameSessionView(
                classRepresentation,
                travels,
                gameSessionId,
                gameSessionDto.name,
                gameSessionDto.shortName,
                gameSessionDto.gameAssets,
                gameSessionDto.timeForGame,
                gameSessionDto.walkingSpeed,
                gameSessionDto.maxTimeAmount,
                gameSessionDto.defaultMoney,
                gameSessionDto.interactionRadius,
                gameSessionDto.maxPlayerAmount
            )
        }
    }

    override suspend fun joinToGame(
        gameJoinRequest: GameJoinCodeRequest,
        loginUserId: LoginUserId,
        userRoles: NonEmptyList<Role>
    ): Effect<JoinGameException, GameJoinResponse> = effect {
        Transactor.dbQuery {
            val gameSessionId = GameSessionDao.findGameSession(gameJoinRequest.gameCode)
                .toEither { JoinGameException.WrongParameter(gameJoinRequest.gameCode) }.bind()
            @Suppress("detekt:NoEffectScopeBindableValueAsStatement")
            when (GameUserDao.getUserInGame(gameSessionId, loginUserId)) {
                is None -> Right(None)
                else -> Left(JoinGameException.UserAlreadyInGame(gameJoinRequest.gameCode, loginUserId))
            }.bind()

            val userAlreadyInGame = GameUserDao.getGameUserInfo(loginUserId, gameSessionId).onSome { playerStatus ->
                val properPlayerId = playerStatus.playerId
                raiseWhen(properPlayerId != gameJoinRequest.playerId) {
                    JoinGameException.WrongPlayerId(
                        properPlayerId,
                        gameSessionId
                    )
                }
            }

            @Suppress("detekt:NoEffectScopeBindableValueAsStatement")
            when (GameUserDao.getUserInGame(gameSessionId, gameJoinRequest.playerId, loginUserId)) {
                is None -> Right(None)
                else -> Left(JoinGameException.DuplicatedPlayerId(gameJoinRequest.gameCode, gameJoinRequest.playerId))
            }.bind()

            userAlreadyInGame.onNone {
                val (className, usage) = GameUserDao.getClassUsages(gameSessionId).toList().minByOrNull { it.second }!!
                logger.info("Using $className for user $loginUserId in game $gameSessionId because it has $usage")
                GameUserDao.insertUser(loginUserId, gameSessionId, gameJoinRequest.playerId, className)
                PlayerResourceDao.insertUserResources(gameSessionId, gameJoinRequest.playerId)
            }

            val token =
                gameAuthService.getGameUserToken(gameSessionId, loginUserId, gameJoinRequest.playerId, userRoles)

            GameJoinResponse(token, gameSessionId)
        }
    }
}
