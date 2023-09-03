package pl.edu.agh.logs.service

import arrow.core.raise.either
import arrow.core.raise.toEither
import arrow.core.toNonEmptyListOrNone
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import org.koin.ktor.ext.inject
import pl.edu.agh.analytics.dao.Logs
import pl.edu.agh.analytics.service.AnalyticsServiceImpl
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getGameUser
import pl.edu.agh.auth.service.getLoggedUser
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerStatus
import pl.edu.agh.game.domain.`in`.GameInitParameters
import pl.edu.agh.game.domain.`in`.GameJoinCodeRequest
import pl.edu.agh.game.domain.out.GameJoinResponse
import pl.edu.agh.game.domain.out.GameSessionView
import pl.edu.agh.game.service.GameService
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.getParam
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger

object InitRoutes {
    fun Application.configureGameInitRoutes() {
        val logger = getLogger(Application::class.java)

        val gameConfigService by inject<GameService>()
        val analyticsService = AnalyticsServiceImpl()

        routing {
            authenticate(Token.GAME_TOKEN, Role.USER) {
                get("/gameStatus") {
                    Utils.handleOutput(call) {
                        either {
                            val (gameSessionId, loginUserId, _) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Jwt malformed" }
                                .bind()
                            logger.info("User $loginUserId wants to get his status")
                            gameConfigService.getGameUserStatus(gameSessionId, loginUserId)
                                .toEither { HttpStatusCode.NotFound to "Resource not found" }.bind()
                        }.responsePair(PlayerStatus.serializer())
                    }
                }
                get("/settings") {
                    Utils.handleOutput(call) {
                        either {
                            val (gameSessionId, _, _) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Couldn't find payload" }
                                .bind()

                            logger.info("get game for gameSessionId $gameSessionId")
                            gameConfigService.getGameInfo(gameSessionId)
                                .toEither { HttpStatusCode.NotFound to "Resource not found" }.bind()
                        }.responsePair(GameSessionView.serializer())
                    }
                }
            }
            authenticate(Token.LOGIN_USER_TOKEN, Role.ADMIN) {
                post("/admin/createGame") {
                    Utils.handleOutput(call) {
                        either {
                            val gameInitParameters = Utils.getBody<GameInitParameters>(call).bind()
                            val (_, _, loginUserId) = getLoggedUser(call)

                            gameConfigService.createGame(gameInitParameters, loginUserId)
                                .toEither().mapLeft { it.toResponse() }.bind()
                        }.responsePair(GameSessionId.serializer())
                    }
                }
                post("/admin/copyGame/{gameSessionId}") {
                    Utils.handleOutput(call) {
                        either {
                            val gameSessionId: GameSessionId =
                                getParam("gameSessionId") { GameSessionId(it) }.bind()
                            val gameName = getParam("gameName").bind()

                            val (_, _, loginUserId) = getLoggedUser(call)

                            gameConfigService.copyGame(gameSessionId, loginUserId, gameName)
                                .toEither().mapLeft { it.toResponse() }.bind()
                        }.responsePair(GameSessionId.serializer())
                    }
                }
                get("/admin/settings/{gameSessionId}") {
                    Utils.handleOutput(call) {
                        either {
                            val gameSessionId: GameSessionId =
                                getParam("gameSessionId") { GameSessionId(it) }.bind()

                            logger.info("get game for gameSessionId $gameSessionId")
                            gameConfigService.getGameInfo(gameSessionId)
                                .toEither { HttpStatusCode.NotFound to "Resource not found" }.bind()
                        }.responsePair(GameSessionView.serializer())
                    }
                }
                get("/getLogs/{gameSessionId}") {
                    Utils.handleOutput(call) {
                        either {
                            val gameSessionId: GameSessionId =
                                getParam("gameSessionId") { GameSessionId(it) }.bind()

                            logger.info("get game logs for gameSessionId $gameSessionId")
                            analyticsService.getLogs(gameSessionId)
                        }.responsePair(ListSerializer(Logs.serializer()))
                    }
                }
            }
            authenticate(Token.LOGIN_USER_TOKEN, Role.USER, Role.ADMIN) {
                post("/getGameToken") {
                    Utils.handleOutput(call) {
                        either {
                            val (_, roles, loginUserId) = getLoggedUser(call)
                            logger.info("User $loginUserId wants to get gameToken")
                            val gameJoinRequest = Utils.getBody<GameJoinCodeRequest>(call).bind()
                            val rolesNel = roles.toNonEmptyListOrNone()
                                .toEither { HttpStatusCode.Unauthorized to "No roles found for user" }.bind()

                            gameConfigService.joinToGame(gameJoinRequest, loginUserId, rolesNel).toEither()
                                .mapLeft { it.toResponse() }.bind()
                        }.responsePair(GameJoinResponse.serializer())
                    }
                }
            }
        }
    }
}
