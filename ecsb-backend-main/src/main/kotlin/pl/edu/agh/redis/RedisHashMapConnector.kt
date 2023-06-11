package pl.edu.agh.redis

import arrow.core.Option
import arrow.core.toOption
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import pl.edu.agh.utils.LoggerDelegate

class RedisHashMapConnector<S, K, V> private constructor(
    private val prefix: String,
    private val toName: (S) -> String,
    private val kSerializer: KSerializer<K>,
    private val vSerializer: KSerializer<V>,
    private val redisClient: KredsClient
) {
    private val logger by LoggerDelegate()

    private fun getName(name: S) = "$prefix${toName(name)}"

    suspend fun getAll(name: S): Map<K, V> {
        logger.info("Requesting ${getName(name)} from redis")
        return redisClient.hgetAll(getName(name)).withIndex().partition {
            it.index % 2 == 0
        }.let { (even, odd) ->
            logger.info((even to odd).toString())
            even.map { it.value } zip odd.map { it.value }
        }.associate { (key, value) ->
            Json.decodeFromString(kSerializer, key) to Json.decodeFromString(vSerializer, value)
        }
    }

    suspend fun findOne(name: S, key: K): Option<V> {
        logger.info("Requesting ${getName(name)}: $key from redis")
        return redisClient.hget(getName(name), Json.encodeToString(kSerializer, key)).toOption()
            .map { Json.decodeFromString(vSerializer, it) }
    }

    suspend fun changeData(name: S, key: K, value: V) = redisClient.hset(
        getName(name),
        Json.encodeToString(kSerializer, key) to Json.encodeToString(vSerializer, value)
    )

    suspend fun removeElement(name: S, key: K) = redisClient.hdel(getName(name), Json.encodeToString(kSerializer, key))

    companion object {

        fun <S, K, V> createAsResource(
            redisConfig: RedisConfig,
            prefix: String,
            toName: (S) -> String,
            kSerializer: KSerializer<K>,
            vSerializer: KSerializer<V>
        ): Resource<RedisHashMapConnector<S, K, V>> = resource(
            acquire = {
                val redisClient = newClient(Endpoint.from("${redisConfig.host}:${redisConfig.port}"))

                val redisHashMapConnector = RedisHashMapConnector(prefix, toName, kSerializer, vSerializer, redisClient)

                redisClient to redisHashMapConnector
            },
            release = { resourceValue, _ ->
                val (redisClient, _) = resourceValue
                redisClient.close()
            }
        ).map { it.second }

        const val MOVEMENT_DATA_PREFIX = "movementData"
        const val INTERACTION_DATA_PREFIX = "interactionStatusData"
    }
}
