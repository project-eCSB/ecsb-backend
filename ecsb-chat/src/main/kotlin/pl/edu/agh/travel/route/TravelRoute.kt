package pl.edu.agh.travel.route

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getGameUser
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.game.service.GameStartCheck
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.service.TravelChoosingService
import pl.edu.agh.travel.service.TravelCoopService
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger

class TravelRoute(
    private val travelChoosingService: TravelChoosingService,
) {

    suspend fun handleTravelChoosing(
        webSocketUserParams: WebSocketUserParams,
        message: ChatMessageADT.UserInputMessage.TravelChoosing
    ) {
        val (_, playerId, gameSessionId) = webSocketUserParams
        when (message) {
            ChatMessageADT.UserInputMessage.TravelChoosing.TravelChoosingStart -> {
                travelChoosingService.setInTravel(gameSessionId, playerId)
            }

            ChatMessageADT.UserInputMessage.TravelChoosing.TravelChoosingStop -> {
                travelChoosingService.removeInTravel(gameSessionId, playerId)
            }

            is ChatMessageADT.UserInputMessage.TravelChoosing.TravelChange -> {
                travelChoosingService.changeTravelDestination(gameSessionId, playerId, message.travelName)
            }
        }
    }

    companion object {
        fun Application.configureTravelRoute() = routing {
            val logger = getLogger(Application::class.java)
            val travelCoopService by inject<TravelCoopService>()

            authenticate(Token.GAME_TOKEN, Role.USER) {
                post("/travel") {
                    Utils.handleOutput(call) {
                        either {
                            val (gameSessionId, _, playerId) = getGameUser(call).toEither {
                                HttpStatusCode.Unauthorized to "Couldn't find payload"
                            }.bind()
                            GameStartCheck.checkGameStartedAndNotEnded(
                                gameSessionId,
                                playerId
                            ) {}(logger).mapLeft { HttpStatusCode.BadRequest to it }.bind()
                            val gameCityName = Utils.getBody<TravelName>(call).bind()
                            logger.info("User $playerId conducts coop travel to $gameCityName in game $gameSessionId")
                            travelCoopService.conductPlayerTravel(
                                gameSessionId,
                                playerId,
                                gameCityName
                            ).mapLeft { it.toResponsePairLogging() }.bind()
                        }.responsePair()
                    }
                }
            }
        }
    }
}
