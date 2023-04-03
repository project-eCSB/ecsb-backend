package pl.edu.agh.messages.service

import pl.edu.agh.messages.domain.MessageSenderData

interface MessagePasser<T> {
    suspend fun broadcast(senderId: MessageSenderData, message: T)
}