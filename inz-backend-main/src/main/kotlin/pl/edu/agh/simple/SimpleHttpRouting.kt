package pl.edu.agh.simple

import arrow.core.Option
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import pl.edu.agh.plugins.Transactor
import pl.edu.agh.plugins.Utils.getOption
import pl.edu.agh.plugins.Utils.handleOutput
import pl.edu.agh.plugins.Utils.responsePair
import pl.edu.agh.plugins.createToken
import pl.edu.agh.simple.di.DIImplementation
import pl.edu.agh.simple.di.DIInterface
import pl.edu.agh.simple.di.DIWithDependency

fun Application.installKoin() {
    val basicModule = module {
        single<DIInterface> { DIImplementation { pp -> println(pp) } }
        singleOf(::DIWithDependency)
    }

    install(Koin) {
        slf4jLogger()
        modules(basicModule)
    }
}

fun Application.SimpleHttpRouting() {
    routing {

        val service by inject<DIInterface>()
        val serviceDIWithDependency by inject<DIWithDependency>()

        authenticate("jwt") {
            get("/{id}") {
                handleOutput(call) {
                    val id = call.parameters.getOption("id").flatMap { Option.fromNullable(it.toIntOrNull()) }
                    id.flatMap { id ->
                        Transactor.dbQuery {
                            SimpleTable.getById(id)
                        }
                    }.toEither { "No input present" }.responsePair(SimpleTable.SimpleTableDTO.serializer())
                }
            }
        }
        post("/") {
            handleOutput(call) {
                val payload = call.receive<Map<String, String>>()
                val name = Option.fromNullable(payload["name"])
                name.map { name ->
                    mapOf(
                        "token" to this@SimpleHttpRouting.createToken(name)
                    )
                }.toEither { "No input present" }.responsePair(String.serializer(), String.serializer())
            }
        }
        get("/") {
            val mapToBePrinted = mapOf(Pair("hello", 123))
            service.printJson(mapToBePrinted, MapSerializer(String.serializer(), Int.serializer()))
            call.respond("hello")
        }
    }
}