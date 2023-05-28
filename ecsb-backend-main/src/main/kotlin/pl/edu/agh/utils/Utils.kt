package pl.edu.agh.utils

import arrow.core.*
import arrow.core.raise.Effect
import arrow.core.raise.option
import arrow.core.raise.toEither
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.slf4j.Logger
import java.io.File

object Utils {
    @JvmName("responsePairList")
    fun <T : Either<String, List<R>>, R : Any> T.responsePair(serializer: KSerializer<R>) =
        this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairMapList")
    fun <T : Either<String, Map<K, List<V>>>, K : Any, V : Any> T.responsePair(
        serializer: KSerializer<K>, serializer2: KSerializer<V>
    ) = this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairMap")
    fun <T : Either<String, Map<K, V>>, K : Any, V : Any> T.responsePair(
        serializer: KSerializer<K>, serializer2: KSerializer<V>
    ) = this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairAny")
    fun <T : Either<String, R>, R : Any> T.responsePair(serializer: KSerializer<R>) =
        this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairEitherPair")
    fun <T : Either<Pair<HttpStatusCode, String>, R>, R : Any> T.responsePair(serializer: KSerializer<R>) =
        this.fold({ (it.first to it.second) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairEitherList")
    fun <T : Either<Pair<HttpStatusCode, String>, List<R>>, R : Any> T.responsePair(serializer: KSerializer<R>) =
        this.fold({ (it.first to it.second) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairEitherUnit")
    fun <T : Either<Pair<HttpStatusCode, String>, Unit>> T.responsePair() =
        this.fold({ (it.first to it.second) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairList")
    fun <T : List<R>, R> T.responsePair(serializer: KSerializer<R>) = (HttpStatusCode.OK to this)

    suspend fun handleOutputFile(
        call: ApplicationCall,
        output: suspend (ApplicationCall) -> Either<Pair<HttpStatusCode, String>, File>
    ): Unit = output(call).fold(
        ifLeft = { (status, value) ->
            call.respond(status, value)
        }, ifRight = { file ->
            call.respondFile(file)
        })

    suspend inline fun <reified T : Any> handleOutput(
        call: ApplicationCall, output: (ApplicationCall) -> Pair<HttpStatusCode, T>
    ): Unit = Either.catch { output(call) }
        .onLeft { getLogger(Application::class.java).error("Route failed with", it) }.getOrNull()!!
        .let { (status, value) ->
            call.respond(status, value)
        }

    fun Parameters.getOption(id: String): Option<String> {
        return Option.fromNullable(this[id])
    }

    suspend inline fun <reified T : Any> getBody(call: ApplicationCall) = Either.catch { call.receive<T>() }.mapLeft {
        getLogger(Application::class.java).error("Error while parsing body", it)
        Pair(HttpStatusCode.UnsupportedMediaType, "Body malformed")
    }

    fun PipelineContext<Unit, ApplicationCall>.getParam(name: String): Either<Pair<HttpStatusCode, String>, String> =
        Option.fromNullable(call.parameters[name])
            .toEither { Pair(HttpStatusCode.BadRequest, "Missing parameter $name") }

    suspend fun <T> PipelineContext<Unit, ApplicationCall>.getParam(
        name: String, transform: (Int) -> T
    ): Either<Pair<HttpStatusCode, String>, T> = option {
        val strParam = Option.fromNullable(call.parameters[name]).bind()
        val intParam = strParam.toIntOrNull().toOption().bind()
        transform(intParam)
    }.fold({ Pair(HttpStatusCode.BadRequest, "Missing parameter $name").left() }, { it.right() })

    suspend fun <L : DomainException, R> Effect<L, R>.toResponsePairLogging(): Either<Pair<HttpStatusCode, String>, R> =
        this.toEither().mapLeft { it.toResponsePairLogging() }

    fun <T> catchPrint(logger: Logger, function: () -> T): T =
        runCatching(function)
            .onFailure { logger.error("failed catchPrint()", it) }
            .getOrThrow()

    suspend fun <L, R> Either<L, R>.leftFlatMap(op: suspend (L) -> Either<L, R>): Either<L, R> =
        fold(ifLeft = {
            op(it)
        }, ifRight = {
            it.right()
        })


    suspend fun <T> repeatUntilFulfilled(times: Int, f: Effect<Throwable, T>): Either<Throwable, T> =
        f.toEither().leftFlatMap {
            if (times == 0)
                it.left()
            else repeatUntilFulfilled(times - 1, f)
        }


    fun <T : Table, R> ResultRow.getCol(alias: Alias<T>?, column: Column<R>): R = this[alias?.get(column) ?: column]
}
