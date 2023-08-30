package pl.edu.agh.messages.service.simple

import arrow.core.*
import arrow.core.raise.option
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.messages.domain.MessageWrapper
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.utils.LoggerDelegate

class SimpleMessagePasser<T> private constructor(
    channel: Channel<MessageWrapper<T>>
) : MessagePasser<T>(channel) {

    class SimpleConsumer<T>(
        private val channel: Channel<MessageWrapper<T>>,
        private val sessionStorage: SessionStorage<WebSocketSession>,
        private val kSerializer: KSerializer<T>
    ) {
        suspend fun consume() {
            logger.info("Start consuming messages")
            channel.consumeAsFlow().onEach {
                when (it.sendTo) {
                    None -> broadcast(it.gameSessionId, it.senderId, it.message)
                    is Some -> multicast(it.gameSessionId, it.senderId, it.sendTo.value, it.message)
                }
            }.collect()
        }

        private suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: T) {
            logger.info("[Sending] Broadcasting message $message from $senderId")
            sessionStorage.getSessions(gameSessionId)?.forEach { (user, session) ->
                if (user != senderId) {
                    Either.catch { session.outgoing.send(Frame.Text(Json.encodeToString(kSerializer, message))) }
                        .onLeft { logger.error("Message passer thrown exception, $it", it) }
                }
            }
        }

        private suspend fun multicast(
            gameSessionId: GameSessionId,
            fromId: PlayerId,
            toIds: NonEmptySet<PlayerId>,
            message: T
        ) {
            logger.info("[Sending] Multicasting message $message from $fromId to $toIds")
            option {
                val sessions = Option.fromNullable(sessionStorage.getSessions(gameSessionId)).bind()
                toIds.forEach { playerId ->
                    Either.catch {
                        sessions[playerId]?.outgoing?.send(
                            Frame.Text(Json.encodeToString(kSerializer, message))
                        )
                    }.onLeft { logger.error("Message passer thrown exception, $it", it) }
                }
            }.getOrElse { logger.warn("Game session $gameSessionId not found") }
        }
    }

    companion object {
        private val logger by LoggerDelegate()

        @OptIn(DelicateCoroutinesApi::class)
        fun <T> create(
            sessionStorage: SessionStorage<WebSocketSession>,
            kSerializer: KSerializer<T>
        ): Resource<SimpleMessagePasser<T>> = resource(acquire = {
            val channel = Channel<MessageWrapper<T>>(UNLIMITED)
            val job = GlobalScope.launch {
                val simpleConsumer = SimpleConsumer<T>(channel, sessionStorage, kSerializer)
                simpleConsumer.consume()
            }
            Triple(SimpleMessagePasser(channel), job, channel)
        }, release = { resourceValue, _ ->
            val (_, job, channel) = resourceValue
            channel.cancel()
            job.cancel()
            logger.info("End of SimpleMessagePasser resource")
        }).map { it.first }
    }
}
