package pl.edu.agh.production.route

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
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.production.domain.WorkshopInternalMessages
import pl.edu.agh.production.service.ProductionService
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger

class ProductionRoute(
    private val productionService: ProductionService,
    private val productionProducer: InteractionProducer<WorkshopInternalMessages>
) {

    suspend fun handleWorkshopMessage(
        webSocketUserParams: WebSocketUserParams,
        message: ChatMessageADT.UserInputMessage.WorkshopMessages
    ) {
        val (_, playerId, gameSessionId) = webSocketUserParams
        when (message) {
            ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopChoosingStart -> {
                productionService.setInWorkshop(gameSessionId, playerId)
            }

            ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopChoosingStop -> {
                productionService.removeInWorkshop(gameSessionId, playerId)
            }

            is ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopChoosingChange -> {
                productionService.changeSelectedValues(gameSessionId, playerId, message.amount)
            }

            is ChatMessageADT.UserInputMessage.WorkshopMessages.WorkshopStart -> productionProducer.sendMessage(
                gameSessionId, playerId, WorkshopInternalMessages.WorkshopStart(message.amount)
            )
        }
    }

    companion object {
        fun Application.configureProductionRoute() = routing {
            val logger = getLogger(Application::class.java)
            val productionService by inject<ProductionService>()

            authenticate(Token.GAME_TOKEN, Role.USER) {
                post("/production") {
                    Utils.handleOutput(call) {
                        either {
                            val (gameSessionId, _, playerId) = getGameUser(call).toEither {
                                HttpStatusCode.Unauthorized to "Couldn't find payload"
                            }.bind()
                            GameStartCheck.checkGameStartedAndNotEnded(
                                gameSessionId,
                                playerId
                            ) {}(logger).mapLeft { HttpStatusCode.BadRequest to it }.bind()
                            val quantity = Utils.getBody<PosInt>(call).bind()

                            logger.info("User $playerId wants to conduct a production in game $gameSessionId")
                            productionService.conductPlayerProduction(
                                gameSessionId,
                                quantity,
                                playerId
                            ).mapLeft { it.toResponsePairLogging() }.bind()
                        }.responsePair()
                    }
                }
            }
        }
    }
}
