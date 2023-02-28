package pl.edu.agh.simple

import arrow.core.Option
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.edu.agh.plugins.Transactor
import pl.edu.agh.plugins.Utils.handleOutput
import pl.edu.agh.plugins.Utils.responsePair

fun Application.SimpleHttpRouting() {
    routing {
        get("/{id}") {
            handleOutput(call) {
                val maybeId = call.parameters["id"]?.toIntOrNull()
                val id = Option.fromNullable(maybeId)
                id.flatMap { id ->
                    Transactor.dbQuery {
                        SimpleTable.getById(id)
                    }
                }.toEither { "No input viable" }.responsePair(SimpleTable.SimpleTableDTO.serializer())
            }
        }
        get("/") {
            call.respond("hello")
        }
    }
}