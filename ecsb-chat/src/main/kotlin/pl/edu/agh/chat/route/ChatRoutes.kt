package pl.edu.agh.chat.route

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.chat.domain.*
import pl.edu.agh.coop.service.CoopService
import pl.edu.agh.domain.LogsMessage
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.production.route.ProductionRoute
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.travel.route.TravelRoute
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop

object ChatRoutes {
    fun Application.configureChatRoutes(gameJWTConfig: JWTConfig<Token.GAME_TOKEN>) {
        val logger = getLogger(Application::class.java)
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val productionRoute by inject<ProductionRoute>()
        val travelRoute by inject<TravelRoute>()
        val coopService by inject<CoopService>()
        val tradeService by inject<TradeService>()
        val logsProducer by inject<InteractionProducer<LogsMessage>>()
        val timeProducer by inject<InteractionProducer<TimeInternalMessages>>()

        fun initMovePlayer(webSocketUserParams: WebSocketUserParams, webSocketSession: WebSocketSession) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
        }

        suspend fun mainBlock(
            webSocketUserParams: WebSocketUserParams,
            message: ChatMessageADT.UserInputMessage
        ) {
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is ChatMessageADT.UserInputMessage.WorkshopChoosing -> productionRoute.handleWorkshopChoosing(
                    webSocketUserParams,
                    message
                )

                is ChatMessageADT.UserInputMessage.TravelChoosing -> travelRoute.handleTravelChoosing(
                    webSocketUserParams,
                    message
                )

                is TradeMessages.TradeUserInputMessage -> tradeService.handleIncomingTradeMessage(
                    webSocketUserParams.gameSessionId,
                    webSocketUserParams.playerId,
                    message
                )

                is CoopMessages.CoopUserInputMessage -> coopService.handleIncomingCoopMessage(
                    webSocketUserParams.gameSessionId,
                    webSocketUserParams.playerId,
                    message
                )

                is ChatMessageADT.UserInputMessage.UserClickedOn -> logsProducer.sendMessage(
                    webSocketUserParams.gameSessionId,
                    webSocketUserParams.playerId,
                    LogsMessage.UserClickedOn(message.name)
                )

                is TimeMessages.TimeUserInputMessage.GameTimeSyncRequest -> timeProducer.sendMessage(
                    webSocketUserParams.gameSessionId,
                    webSocketUserParams.playerId,
                    TimeInternalMessages.GameTimeSyncMessage
                )
            }
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId")
            tradeService.cancelAllPlayerTrades(gameSessionId, playerId)
            sessionStorage.removeSession(gameSessionId, playerId)
        }

        routing {
            webSocket("/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUserWS(gameJWTConfig).bind()

                    Either.catch {
                        startMainLoop(
                            logger,
                            ChatMessageADT.UserInputMessage.serializer(),
                            webSocketUserParams,
                            ::initMovePlayer,
                            ::closeConnection,
                            ::mainBlock
                        )
                    }.mapLeft {
                        logger.error("Error while starting main loop: $it", it)
                        "Error initializing user"
                    }.bind()
                }.mapLeft {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, it))
                }
            }
        }
    }
}
