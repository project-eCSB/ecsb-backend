package pl.edu.agh.redis

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.redis.lettucemod.RedisModulesClient
import com.redis.lettucemod.api.reactive.RedisModulesReactiveCommands
import com.redis.lettucemod.cluster.RedisModulesClusterClient
import com.redis.lettucemod.search.CreateOptions
import com.redis.lettucemod.search.NumericField
import com.redis.lettucemod.search.SearchOptions
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisURI
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.toKotlin
import java.time.Duration

class RedisJsonConnector<K, V> private constructor(
    private val prefix: String,
    private val kSerializer: KSerializer<K>,
    private val vSerializer: KSerializer<V>,
    private val redisClient: RedisModulesReactiveCommands<String, String>,
    private val expireKeys: Boolean
) {

    private fun keyToName(key: K): String = Json.encodeToString(kSerializer, key)

    @Serializable
    private data class RedisJsonValue<K, V>(val namespace: GameSessionId, val key: K, val value: V) {
        fun toPair(): Pair<K, V> = key to value
    }

    private fun getName(name: GameSessionId, key: K): String =
        "$prefix:{${name.value}}:${keyToName(key)}"

    suspend fun getAll(name: GameSessionId): Map<K, V> {
        logger.info("Requesting $prefix $name from redis")
        return redisClient
            .ftSearch(
                "$prefix-idx",
                "@namespace:[${name.value},${name.value}]",
                SearchOptions.builder<String, String>().limit(0L, maxPlayersInGame).build()
            )
            .toKotlin()
            .map {
                it.associate { doc ->
                    Json.decodeFromString(serializer, doc.values.first()).toPair()
                }
            }
            .getOrElse { mapOf() }
    }

    suspend fun findOne(name: GameSessionId, key: K): Option<V> {
        logger.info("Requesting ${getName(name, key)}: from redis")
        return redisClient.jsonGet(getName(name, key), "$").toKotlin()
            .map { Json.decodeFromString(serializer, it.drop(1).dropLast(1)).value }
    }

    private val serializer = RedisJsonValue.serializer(kSerializer, vSerializer)

    suspend fun changeData(name: GameSessionId, key: K, value: V) {
        redisClient.jsonSet(
            getName(name, key),
            "$",
            Json.encodeToString(serializer, RedisJsonValue(name, key, value))
        ).flatMap {
            if (expireKeys) {
                redisClient.expire(getName(name, key), Duration.ofHours(2))
            } else {
                mono {}
            }
        }.toKotlin()
    }

    suspend fun removeElement(name: GameSessionId, key: K) =
        redisClient.del(getName(name, key)).toKotlin().map { }.getOrElse { }

    companion object {
        private val logger by LoggerDelegate()

        fun <K, V> createAsResource(redisCreationParams: RedisCreationParams<K, V>): Resource<RedisJsonConnector<K, V>> =
            createAsResource(
                redisCreationParams.redisConfig,
                redisCreationParams.prefix,
                redisCreationParams.kSerializer,
                redisCreationParams.vSerializer
            )

        private fun <K, V> createAsResource(
            redisConfig: RedisConfig,
            prefix: String,
            kSerializer: KSerializer<K>,
            vSerializer: KSerializer<V>
        ): Resource<RedisJsonConnector<K, V>> = resource(
            acquire = {
                val (connection, client) = when (redisConfig.mode) {
                    RedisMode.CLUSTER -> {
                        val uris = redisConfig.hosts.map { config ->
                            RedisURI.create(config.host, config.port)
                        }
                        val clientCluster = RedisModulesClusterClient.create(uris)
                        val connectionCluster = clientCluster.connect()

                        connectionCluster to clientCluster
                    }

                    RedisMode.SINGLE_NODE -> {
                        val connectionConfig = redisConfig.hosts.first()
                        val client =
                            RedisModulesClient.create(RedisURI.create(connectionConfig.host, connectionConfig.port))
                        val connection = client.connect()

                        connection to client
                    }
                }
                Either.catch {
                    connection.sync().ftCreate(
                        "$prefix-idx",
                        CreateOptions.builder<String, String>().prefix(prefix).on(CreateOptions.DataType.JSON).build(),
                        NumericField.Builder("$.namespace").`as`("namespace").build()
                    )
                }.onLeft {
                    when {
                        (it is RedisCommandExecutionException) -> logger.error("Index creation thrown exception: ", it)
                        else -> throw it
                    }
                }

                val redisHashMapConnector =
                    RedisJsonConnector(prefix, kSerializer, vSerializer, connection.reactive(), redisConfig.expireKeys)

                Triple(connection, client, redisHashMapConnector)
            },
            release = { resourceValue, _ ->
                val (connection, client, _) = resourceValue
                connection.close()
                client.shutdown()
            }
        ).map { it.third }

        private const val maxPlayersInGame: Long = 100L
    }
}
