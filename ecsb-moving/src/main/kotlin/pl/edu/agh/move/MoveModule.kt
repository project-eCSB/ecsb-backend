package pl.edu.agh.move

import io.ktor.server.application.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import pl.edu.agh.messages.domain.MessageSenderData
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.service.MessagePasserImpl
import pl.edu.agh.move.service.SessionStorageImpl

object MoveModule {
    fun Application.getKoinMoveModule() = module {
        singleOf<SessionStorage<MessageSenderData>>(::SessionStorageImpl)
        single<MessagePasser<Message>> { MessagePasserImpl(get()) }
    }
}