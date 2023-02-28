package pl.edu.agh.simple

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.edu.agh.plugins.Transactor
import pl.edu.agh.plugins.Utils.getOption
import pl.edu.agh.plugins.Utils.handleOutput
import pl.edu.agh.plugins.Utils.responsePair
import pl.edu.agh.plugins.Utils.toInt

fun Application.SimpleHttpRouting() {
    routing {
        get("/{id}") {
            handleOutput(call) {
                val id = call.parameters.getOption("id").toInt()
                id.flatMap { id ->
                    Transactor.dbQuery {
                        SimpleTable.getById(id)
                    }
                }.toEither { "No input present" }.responsePair(SimpleTable.SimpleTableDTO.serializer())
            }
        }
        get("/") {
            call.respond("hello")
        }
    }
}