package pl.edu.agh.init.route

import arrow.core.raise.either
import arrow.core.raise.toEither
import arrow.core.toNonEmptyListOrNone
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getGameUser
import pl.edu.agh.auth.service.getLoggedUser
import pl.edu.agh.domain.*
import pl.edu.agh.init.domain.`in`.GameInitParameters
import pl.edu.agh.init.domain.`in`.GameJoinCodeRequest
import pl.edu.agh.init.domain.out.GameJoinResponse
import pl.edu.agh.init.domain.out.GameSessionView
import pl.edu.agh.init.service.GameConfigService
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.getParam
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger

object InitRoutes {
    fun Application.configureGameInitRoutes() {
        val logger = getLogger(Application::class.java)

        val gameConfigService by inject<GameConfigService>()

        routing {
            route("/init") {
                authenticate(Token.GAME_TOKEN, Role.USER) {
                    get("/gameStatus/") {
                        Utils.handleOutput(call) {
                            either {
                                val (gameSessionId, loginUserId) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Jwt malformed" }
                                    .bind()

                                gameConfigService.getGameUserStatus(gameSessionId, loginUserId)
                                    .toEither { HttpStatusCode.NotFound to "Resource not found" }.bind()
                            }.responsePair(PlayerStatus.serializer())
                        }
                    }
                    get("/settings") {
                        Utils.handleOutput(call) {
                            either {
                                val (gameSessionId, _) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Couldn't find payload" }
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

                                // TODO ADD TILED PARSER HERE <-
                                val coords = Coordinates(3, 3)
                                val direction = Direction.DOWN

                                gameConfigService.createGame(gameInitParameters, coords, direction, loginUserId)
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
                }
                authenticate(Token.LOGIN_USER_TOKEN, Role.USER, Role.ADMIN) {
                    post("/getGameToken") {
                        Utils.handleOutput(call) {
                            either {
                                val (_, roles, loginUserId) = getLoggedUser(call)
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
}
