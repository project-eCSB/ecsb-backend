package pl.edu.agh.plugins

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.Option
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

object Utils {
    @JvmName("responsePairList")
    fun <T : Either<String, List<R>>, R : Any> T.responsePair(serializer: KSerializer<R>) =
        this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairMapList")
    fun <T : Either<String, Map<K, List<V>>>, K : Any, V: Any> T.responsePair(serializer: KSerializer<K>, serializer2: KSerializer<V>) =
        this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairMap")
    fun <T : Either<String, Map<K, V>>, K : Any, V: Any> T.responsePair(serializer: KSerializer<K>, serializer2: KSerializer<V>) =
        this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })

    @JvmName("responsePairAny")
    fun <T : Either<String, R>, R : Any> T.responsePair(serializer: KSerializer<R>) =
        this.fold({ (HttpStatusCode.BadRequest to it) }, { (HttpStatusCode.OK to it) })


    suspend inline fun <reified T : Any> handleOutput(
        call: ApplicationCall, output: (ApplicationCall) -> Pair<HttpStatusCode, T>
    ): Unit {
        return output(call).let { (status, value) ->
            call.respond(status, value)
        }
    }

    fun Parameters.getOption(id: String): Option<String> {
        return Option.fromNullable(this[id])
    }

    fun <T> List<T>.toNel(): Option<Nel<T>> {
        return NonEmptyList.fromList(this)
    }

}
