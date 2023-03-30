package pl.edu.agh.utils

import arrow.core.*
import arrow.core.continuations.Effect
import arrow.core.continuations.option
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer

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


    suspend inline fun <reified T : Any> handleOutput(
        call: ApplicationCall, output: (ApplicationCall) -> Pair<HttpStatusCode, T>
    ) {
        return output(call).let { (status, value) ->
            call.respond(status, value)
        }
    }

    fun Parameters.getOption(id: String): Option<String> {
        return Option.fromNullable(this[id])
    }

    suspend inline fun <reified T : Any> getBody(call: ApplicationCall) = Either.catch { call.receive<T>() }.mapLeft {
        getLogger(Application::class.java).error("Error while parsing body", it)
        Pair(HttpStatusCode.UnsupportedMediaType, "Body malformed")
    }

    suspend fun <T> PipelineContext<Unit, ApplicationCall>.getParam(
        name: String, transform: (Int) -> T
    ): Either<Pair<HttpStatusCode, String>, T> = option {
        val strParam = Option.fromNullable(call.parameters[name]).bind()
        val intParam = strParam.toIntOrNull().toOption().bind()
        transform(intParam)
    }.fold({ Pair(HttpStatusCode.BadRequest, "Missing parameter $name").left() }, { it.right() })


    suspend fun <L : DomainException, R> Effect<L, R>.toResponsePairLogging() =
        this.toEither().mapLeft { it.toResponsePairLogging() }

}
