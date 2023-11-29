package pl.edu.agh.chat.route

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.chat.domain.TimeMessages
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.coop.service.CoopService
import pl.edu.agh.game.service.GameStartCheck
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.logs.domain.LogsMessage
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.production.route.ProductionRoute
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.travel.route.TravelRoute
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop
import java.util.concurrent.atomic.AtomicLong

object ChatRoutes {
    fun Application.configureChatRoutes(
        gameJWTConfig: JWTConfig<Token.GAME_TOKEN>,
        logsProducer: InteractionProducer<LogsMessage>,
        timeProducer: InteractionProducer<TimeInternalMessages>,
        playerCountGauge: AtomicLong,
        prometheusMeterRegistry: PrometheusMeterRegistry,
    ) {
        val logger = getLogger(Application::class.java)
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val productionRoute by inject<ProductionRoute>()
        val travelRoute by inject<TravelRoute>()
        val coopService by inject<CoopService>()
        val tradeService by inject<TradeService>()

        suspend fun initMovePlayer(
            webSocketUserParams: WebSocketUserParams,
            webSocketSession: WebSocketSession
        ): Either<String, Unit> {
            playerCountGauge.incrementAndGet()
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            return GameStartCheck.checkGameStartedAndNotEnded(
                gameSessionId,
                playerId
            ) { sessionStorage.addSession(gameSessionId, playerId, webSocketSession) }(logger)
        }

        suspend fun mainBlock(
            webSocketUserParams: WebSocketUserParams,
            message: ChatMessageADT.UserInputMessage
        ) {
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            prometheusMeterRegistry.counter(
                "chat.messagesSent",
                "gameSessionId",
                webSocketUserParams.gameSessionId.value.toString(),
                "playerId",
                webSocketUserParams.playerId.value
            ).increment()
            when (message) {
                is ChatMessageADT.UserInputMessage.WorkshopMessages -> productionRoute.handleWorkshopMessage(
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

                is ChatMessageADT.UserInputMessage.SyncAdvertisement -> {
                    tradeService.syncAdvertisement(
                        webSocketUserParams.gameSessionId,
                        webSocketUserParams.playerId
                    )
                    coopService.syncAdvertisement(
                        webSocketUserParams.gameSessionId,
                        webSocketUserParams.playerId
                    )
                }
            }
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId")
            tradeService.cancelAllPlayerTrades(gameSessionId, playerId)
            coopService.cancelCoopNegotiationAndAdvertisement(gameSessionId, playerId)
            sessionStorage.removeSession(gameSessionId, playerId)
            playerCountGauge.decrementAndGet()
        }

        this.environment.monitor.subscribe(ApplicationStopPreparing) {
            logger.info("Closing all connections")
            runBlocking {
                sessionStorage.getAllSessions()
                    .forEach { (_, players) ->
                        players.forEach { (_, session) ->
                            session.close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Server restart"))
                        }
                    }
            }
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
