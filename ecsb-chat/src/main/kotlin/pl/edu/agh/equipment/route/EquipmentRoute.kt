package pl.edu.agh.equipment.route

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getGameUser
import pl.edu.agh.equipment.domain.PlayerEquipment
import pl.edu.agh.equipment.service.EquipmentService
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger

object EquipmentRoute {
    fun Application.configureEquipmentRoute() = routing {
        val logger = getLogger(Application::class.java)
        val equipmentService by inject<EquipmentService>()

        authenticate(Token.GAME_TOKEN, Role.USER) {
            get("/equipment") {
                Utils.handleOutput(call) {
                    either {
                        val (gameSessionId, _, playerId) = getGameUser(call).toEither {
                            HttpStatusCode.Unauthorized to "Couldn't find payload"
                        }.bind()

                        logger.info("get equipment for user $playerId from game $gameSessionId")
                        equipmentService.getGameUserEquipment(gameSessionId, playerId)
                            .toEither { HttpStatusCode.NotFound to "Resource not found" }.bind()
                    }.responsePair(PlayerEquipment.serializer())
                }
            }
        }
    }
}
