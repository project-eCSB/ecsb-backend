package pl.edu.agh.messages.service

import arrow.core.NonEmptySet
import arrow.core.None
import arrow.core.nonEmptySetOf
import arrow.core.some
import kotlinx.coroutines.channels.Channel
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.messages.domain.MessageWrapper
import pl.edu.agh.utils.LoggerDelegate

abstract class MessagePasser<T>(private val channel: Channel<MessageWrapper<T>>) {
    val logger by LoggerDelegate()

    suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: T) {
        logger.info("[Passing] Broadcasting message $message from $senderId")
        channel.send(MessageWrapper(message, senderId, gameSessionId, None))
    }

    suspend fun unicast(gameSessionId: GameSessionId, fromId: PlayerId, toId: PlayerId, message: T) {
        logger.info("[Passing] Unicasting message $message from $fromId to $toId")
        channel.send(MessageWrapper(message, fromId, gameSessionId, nonEmptySetOf(toId).some()))
    }

    suspend fun multicast(
        gameSessionId: GameSessionId,
        fromId: PlayerId,
        toIds: NonEmptySet<PlayerId>,
        message: T
    ) {
        logger.info("[Passing] Multicasting message $message from $fromId to $toIds")
        channel.send(MessageWrapper(message, fromId, gameSessionId, toIds.some()))
    }
}
