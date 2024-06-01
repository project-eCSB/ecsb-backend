package pl.edu.agh.game.service

import arrow.core.*
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.*
import io.ktor.http.*
import pl.edu.agh.assets.dao.MapAssetDao
import pl.edu.agh.assets.dao.SavedAssetsDao
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.GameAssets
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.game.dao.GameSessionUserClassesDao
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.game.domain.GameResults
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.domain.requests.GameCreateRequest
import pl.edu.agh.game.domain.requests.GameJoinCodeRequest
import pl.edu.agh.game.domain.responses.GameJoinResponse
import pl.edu.agh.game.domain.responses.GameSettingsResponse
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelDto
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.input.TravelParameters
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

    data class MapNotFound(val message: String) : CreationException() {
        override fun toResponse(): Pair<HttpStatusCode, String> = HttpStatusCode.BadRequest to message
    }

    data class DataNotValid(private val message: String) : CreationException() {
        override fun toResponse(): Pair<HttpStatusCode, String> = HttpStatusCode.BadRequest to message
    }

    data class DefaultAssetsNotFound(private val message: String) : CreationException() {
        override fun toResponse(): Pair<HttpStatusCode, String> = HttpStatusCode.InternalServerError to message
    }
}

interface GameService {
    suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSettingsResponse>
    suspend fun joinToGame(
        gameJoinRequest: GameJoinCodeRequest,
        loginUserId: LoginUserId,
        userRoles: NonEmptyList<Role>
    ): Effect<JoinGameException, GameJoinResponse>

    suspend fun createGame(
        gameCreateRequest: GameCreateRequest,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId>

    suspend fun copyGame(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        gameName: String
    ): Effect<CreationException, GameSessionId>

    suspend fun getGameResults(gameSessionId: GameSessionId): Option<GameResults>

    suspend fun startGame(gameSessionId: GameSessionId): Option<Unit>
}

class GameServiceImpl(
    private val gameAuthService: GameAuthService,
    private val interactionProducer: InteractionProducer<LandingPageMessage>
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
                    travelInfo.time,
                    travelInfo.regenTime
                )
            }.toNonEmptyMapUnsafe()
        }.toNonEmptyMapUnsafe()

        val gameCreateRequest = GameCreateRequest(
            classResourceRepresentation = gameInfo.classResourceRepresentation,
            gameName = gameName,
            travels = travels,
            assets = gameInfo.gameAssets,
            timeForGame = gameInfo.timeForGame,
            maxTimeTokens = gameInfo.maxTimeTokens,
            defaultMoney = gameInfo.defaultMoney,
            walkingSpeed = gameInfo.walkingSpeed,
            interactionRadius = gameInfo.interactionRadius,
            minPlayersToStart = gameInfo.minPlayersToStart
        )

        createGame(gameCreateRequest, loginUserId).bind()
    }

    override suspend fun getGameResults(gameSessionId: GameSessionId): Option<GameResults> = option {
        Transactor.dbQuery {
            val gameSessionName = GameSessionDao.getGameSessionNameAfterEnd(gameSessionId).bind()
            val playersLeaderBoard = GameUserDao.getUsersResults(gameSessionId)

            GameResults(gameSessionName, playersLeaderBoard)
        }
    }

    private fun createGameAssetsWithDefaults(
        sentAssetsIds: NonEmptyMap<FileType, SavedAssetsId>
    ): Either<CreationException, GameAssets> = either {
        val defaultAssets = SavedAssetsDao.getDefaultAssets().getOrElse {
            SavedAssetsDao.markExistingAssetsAsDefault()
            SavedAssetsDao.getDefaultAssets()
                .toEither { CreationException.DefaultAssetsNotFound("Default assets not marked") }.bind()
        }
        val effectiveMapId = sentAssetsIds[FileType.MAP].toOption().getOrElse {
            defaultAssets[FileType.MAP].toOption()
                .map { pair -> pair.id }
                .toEither { CreationException.DefaultAssetsNotFound("Default map not found") }.bind()
        }
        val tileAssetId = sentAssetsIds[FileType.TILE_ASSET_FILE].toOption()
            .getOrElse {
                defaultAssets[FileType.TILE_ASSET_FILE].toOption()
                    .map { pair -> pair.id }
                    .toEither { CreationException.DefaultAssetsNotFound("Default tiles not found") }.bind()
            }
        val characterAssetId = sentAssetsIds[FileType.CHARACTER_ASSET_FILE].toOption()
            .getOrElse {
                defaultAssets[FileType.CHARACTER_ASSET_FILE].toOption()
                    .map { pair -> pair.id }
                    .toEither { CreationException.DefaultAssetsNotFound("Default characters not found") }.bind()
            }
        val resourceAssetsId = sentAssetsIds[FileType.RESOURCE_ASSET_FILE].toOption()
            .getOrElse {
                defaultAssets[FileType.RESOURCE_ASSET_FILE].toOption()
                    .map { pair -> pair.id }
                    .toEither { CreationException.DefaultAssetsNotFound("Default resources not found") }.bind()
            }
        return GameAssets(
            mapAssetId = effectiveMapId,
            tileAssetsId = tileAssetId,
            characterAssetsId = characterAssetId,
            resourceAssetsId = resourceAssetsId
        ).right()
    }

    override suspend fun createGame(
        gameCreateRequest: GameCreateRequest,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId> =
        Transactor.dbQueryEffect<CreationException, GameSessionId>(
            CreationException.UnknownError("Unknown exception happened during creating your game")
        ) {
            either {
                logger.info("Trying to create game from $gameCreateRequest")

                ensure(gameCreateRequest.gameName.isNotBlank()) {
                    CreationException.EmptyString("Game name cannot be empty")
                }

                val gameAssets: GameAssets = createGameAssetsWithDefaults(gameCreateRequest.assets).bind()

                val resources = gameCreateRequest.classResourceRepresentation.map { it.value.gameResourceName }
                raiseWhen(resources.toSet().size != resources.size) {
                    CreationException.EmptyString("Resource name cannot be duplicated in one session")
                }

                val mapAssetDataDto = MapAssetDao.findMapConfig(gameAssets.mapAssetId).toEither {
                    CreationException.MapNotFound("Map ${gameAssets.mapAssetId} not found")
                }.bind()

                val classes = gameCreateRequest.classResourceRepresentation.keys

                ensure(mapAssetDataDto.professionWorkshops.keys.intersect(classes).size == classes.size) {
                    CreationException.DataNotValid(
                        "Classes do not match with equivalent in map asset"
                    )
                }

                val createdGameSessionId =
                    GameSessionDao.createGameSession(
                        gameCreateRequest.gameName,
                        gameAssets,
                        loginUserId,
                        gameCreateRequest.timeForGame,
                        gameCreateRequest.interactionRadius,
                        gameCreateRequest.maxTimeTokens,
                        gameCreateRequest.walkingSpeed,
                        gameCreateRequest.defaultMoney,
                        gameCreateRequest.minPlayersToStart
                    )

                GameSessionUserClassesDao.upsertClasses(
                    gameCreateRequest.classResourceRepresentation,
                    createdGameSessionId
                )

                upsertTravels(
                    createdGameSessionId,
                    gameCreateRequest.travels
                ).bind()

                logger.info("Game created with $gameCreateRequest, its id is $createdGameSessionId")
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
        MapDataTypes.Travel.all().flatTraverse { travelType ->
            either {
                val travelsOfType = travels.getOrNone(travelType).toEither {
                    CreationException.DataNotValid(
                        "Travel ${travelType.dataValue} not valid because they don't exists"
                    )
                }.bind()
                val validatedTravels = travelsOfType.toList().traverse { (travelName, travelParameters) ->
                    either {
                        ensure(travelName.value.isNotBlank()) {
                            CreationException.DataNotValid("Travel name is blank")
                        }
                        TravelDto(
                            createdGameSessionId,
                            travelType,
                            travelName,
                            travelParameters.time,
                            travelParameters.moneyRange,
                            travelParameters.regenTime
                        ) to travelParameters.assets
                    }
                }.bind()
                validatedTravels
            }
        }.bind().map { (inputDto, assets) ->
            TravelDao.insertTravel(inputDto, assets)
        }
    }

    override suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSettingsResponse> = Transactor.dbQuery {
        option {
            logger.info("Getting game session dto for $gameSessionId")
            val gameSessionDto: GameSessionDto = GameSessionDao.getGameSession(gameSessionId).bind()

            logger.info("Getting class representation list for $gameSessionId")
            val classRepresentation = GameSessionUserClassesDao.getClasses(gameSessionId).bind()

            logger.info("Getting travels list for $gameSessionId")
            val travels = TravelDao.getTravels(gameSessionId).bind()

            GameSettingsResponse(
                classRepresentation,
                travels,
                gameSessionId,
                gameSessionDto.name,
                gameSessionDto.shortName,
                gameSessionDto.gameAssets,
                gameSessionDto.timeForGame,
                gameSessionDto.walkingSpeed,
                gameSessionDto.maxTimeTokens,
                gameSessionDto.defaultMoney,
                gameSessionDto.interactionRadius,
                gameSessionDto.minPlayersToStart
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

    override suspend fun startGame(gameSessionId: GameSessionId): Option<Unit> = option {
        Transactor.dbQuery {
            GameSessionDao.startGame(gameSessionId)()
        }.bind()
        interactionProducer.sendMessage(gameSessionId, PlayerIdConst.CHAT_ID, LandingPageMessage.GameStarted)
    }
}
