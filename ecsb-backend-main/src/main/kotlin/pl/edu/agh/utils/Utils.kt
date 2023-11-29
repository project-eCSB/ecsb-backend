package pl.edu.agh.utils

import arrow.core.*
import arrow.core.raise.Effect
import arrow.core.raise.Raise
import arrow.core.raise.option
import arrow.core.raise.toEither
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.koin.core.time.measureTimedValue
import org.slf4j.Logger
import reactor.core.publisher.Mono
import java.io.File
import java.sql.ResultSet
import kotlin.reflect.KFunction2

object Utils {
    @JvmName("responsePairList")
    fun <T : Either<String, List<R>>, R : Any> T.responsePair(serializer: KSerializer<R>) =
        this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairMapList")
    fun <T : Either<String, Map<K, List<V>>>, K : Any, V : Any> T.responsePair(
        serializer: KSerializer<K>,
        serializer2: KSerializer<V>
    ) = this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairMap")
    fun <T : Either<String, Map<K, V>>, K : Any, V : Any> T.responsePair(
        serializer: KSerializer<K>,
        serializer2: KSerializer<V>
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
        },
        ifRight = { file ->
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "ktor_logo.png")
                    .toString()
            )
            call.respondFile(file)
        }
    )

    suspend inline fun <reified T : Any> handleOutput(
        call: ApplicationCall,
        output: (ApplicationCall) -> Pair<HttpStatusCode, T>
    ): Unit = Either.catch { measureTimedValue { output(call) } }
        .fold(
            ifLeft =
            {
                val logger = getLogger(Application::class.java)
                logger.error("Unhandled [${call.request.httpMethod.value}] - ${call.request.uri} Route failed", it)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error :(")
            },
            ifRight = { (response, timeTaken) ->
                val (status, value) = response
                val logger = getLogger(Application::class.java)
                logger.info(
                    "[${call.request.httpMethod.value}] - ${status.value} ${call.request.uri} ${timeTaken}ms $value"
                )
                call.respond(status, value)
            }
        )

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
        name: String,
        transform: (Int) -> T
    ): Either<Pair<HttpStatusCode, String>, T> = option {
        val strParam = Option.fromNullable(call.parameters[name]).bind()
        val intParam = strParam.toIntOrNull().toOption().bind()
        transform(intParam)
    }.fold({ Pair(HttpStatusCode.BadRequest, "Missing parameter $name").left() }, { it.right() })

    fun <T> catchPrint(logger: Logger, function: () -> T): T =
        runCatching(function)
            .onFailure { logger.error("failed catchPrint()", it) }
            .getOrThrow()

    suspend fun <L, R> Either<L, R>.recoverWith(op: suspend (L) -> Either<L, R>): Either<L, R> =
        fold(ifLeft = {
            op(it)
        }, ifRight = {
            it.right()
        })

    suspend fun <T> repeatUntilFulfilled(times: Int, f: Effect<Throwable, T>): Either<Throwable, T> =
        f.toEither().recoverWith {
            if (times == 0) {
                it.left()
            } else {
                repeatUntilFulfilled(times - 1, f)
            }
        }

    fun <T : Table, R> ResultRow.getCol(alias: Alias<T>?, column: Column<R>): R = this[alias?.get(column) ?: column]

    fun <L, E, R> List<E>.flatTraverse(function: (E) -> Either<L, List<R>>): Either<L, List<R>> =
        this.traverse {
            function(it)
        }.map { it.flatten() }
}

fun String.upper() = this.uppercase()
fun String.lower() = this.lowercase()

suspend fun <P1, P2, R> (suspend (P1, P2) -> R).susTupled2(it: Pair<P1, P2>): R =
    this(it.first, it.second)

fun <P1, P2, R> KFunction2<P1, P2, R>.tupled2(tupledd: Pair<P1, P2>): R =
    this(tupledd.first, tupledd.second)

fun <P1, P2, P3, R> ((P1, P2, P3) -> R).tupled(triple: Triple<P1, P2, P3>): R =
    this(triple.first, triple.second, triple.third)

suspend fun <T> Mono<T>.toKotlin(): Option<T> {
    return this.awaitFirstOrNull().toOption()
}

typealias DB<A> = Transaction.() -> A

fun <Error> Raise<Error>.raiseWhen(check: Boolean, err: () -> Error) {
    if (check) raise(err())
}

fun <E> List<E>.tapEach(function: (E) -> Unit): List<E> =
    this.map {
        function(it)
        it
    }

suspend fun <T> Boolean.whenA(ifFalse: () -> T, f: suspend () -> T): T =
    if (this) {
        f()
    } else {
        ifFalse()
    }

fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
    val result = arrayListOf<T>()
    TransactionManager.current().exec(this, explicitStatementType = StatementType.SELECT) { resultSet ->
        while (resultSet.next()) {
            result += transform(resultSet)
        }
    }
    return result
}
