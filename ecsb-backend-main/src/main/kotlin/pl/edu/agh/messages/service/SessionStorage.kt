package pl.edu.agh.messages.service

import io.ktor.server.websocket.*

interface SessionStorage<T> {
    fun addSession(webSocketServerSession: WebSocketServerSession): T
    fun removeSession(user: T)
    fun getSessions(): Map<T, WebSocketServerSession>
    fun getSession(user: T): WebSocketServerSession?
}