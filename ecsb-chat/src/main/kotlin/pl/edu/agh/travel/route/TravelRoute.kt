package pl.edu.agh.travel.route

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getGameUser
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.service.TravelService
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger

object TravelRoute {
    fun Application.configureTravelRoute() = routing {
        val logger = getLogger(Application::class.java)
        val travelService by inject<TravelService>()

        authenticate(Token.GAME_TOKEN, Role.USER) {
            post("/travel") {
                Utils.handleOutput(call) {
                    either {
                        val (gameSessionId, _, playerId) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Couldn't find payload" }
                            .bind()
                        val gameCityName = Utils.getBody<TravelName>(call).bind()

                        logger.info("User $playerId conducts travel to $gameCityName in game $gameSessionId")
                        travelService.conductPlayerTravel(
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