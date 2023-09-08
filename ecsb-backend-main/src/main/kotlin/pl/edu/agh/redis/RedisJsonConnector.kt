package pl.edu.agh.redis

import arrow.core.*
import arrow.core.raise.nullable
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.redis.lettucemod.RedisModulesClient
import com.redis.lettucemod.api.reactive.RedisModulesReactiveCommands
import com.redis.lettucemod.cluster.RedisModulesClusterClient
import com.redis.lettucemod.search.CreateOptions
import com.redis.lettucemod.search.NumericField
import com.redis.lettucemod.search.SearchOptions
import com.redis.lettucemod.search.TagField
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisURI
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.toKotlin

class RedisJsonConnector<K, V> private constructor(
    private val prefix: String,
    private val kSerializer: KSerializer<K>,
    private val vSerializer: KSerializer<V>,
    private val redisClient: RedisModulesReactiveCommands<String, String>
) {
    private val indexName: String = "$prefix-idx"

    private fun mainQuery(gameSessionId: GameSessionId): String =
        "@namespace:{${gameSessionId.value}} @check:{yes}"


    private data class RedisJsonValue<K, V>(
        val namespace: GameSessionId,
        val key: K,
        val value: V,
        val exists: String?
    ) {
        fun toPair(): Pair<K, V> = key to value
    }

    private fun getName(name: GameSessionId, key: K): String =
        "player:{${name.value}}:${configuredJson.encodeToString(kSerializer, key)}"

    private suspend fun queryIndex(gameSessionId: GameSessionId, additionalQuery: Option<String>): Map<K, V> {
        logger.info("Requesting $prefix $gameSessionId from redis")
        return redisClient
            .ftSearch(
                indexName,
                mainQuery(gameSessionId) + additionalQuery.getOrElse { "" },
                SearchOptions.builder<String, String>().limit(0L, maxPlayersInGame).build()
            )
            .toKotlin()
            .map {
                it.map { doc ->
                    configuredJson.decodeFromString(serializerNew, doc.values.first()).map(RedisJsonValue<K, V>::toPair)
                }.flattenOption().toMap()
            }
            .getOrElse { mapOf() }
    }

    suspend fun getAll(gameSessionId: GameSessionId): Map<K, V> {
        logger.info("Requesting $prefix $gameSessionId from redis")
        return queryIndex(gameSessionId, none())
    }

    suspend fun getAllAround(name: GameSessionId, aroundPoint: Coordinates, radius: Int): Map<K, V> {
        logger.info("Requesting around point $aroundPoint in radius $radius of $prefix $name from redis")
        return queryIndex(
            name,
            " @x:[${aroundPoint.x - radius},${aroundPoint.x + radius}] @y:[${aroundPoint.y - radius},${aroundPoint.y + radius}]".some()
        )
    }

    suspend fun initPlayerKey(name: GameSessionId, key: K) {
        redisClient.jsonMerge(
            getName(name, key),
            "$",
            """{"namespace": "${Json.encodeToString(GameSessionId.serializer(), name)}",
                    "key": ${Json.encodeToString(kSerializer, key)},
                    ${RedisPrefixes.values().joinToString(",") { "\"${existsField(it.prefix)}\": \"nope\"" }}
                    }"""
        ).toKotlin()
    }

    suspend fun findOne(name: GameSessionId, key: K): Option<V> {
        logger.info("Requesting ${getName(name, key)}: from redis")
        return redisClient.jsonGet(getName(name, key), "$").toKotlin()
            .flatMap { configuredJson.decodeFromString(serializerNew, it.drop(1).dropLast(1)) }.map { it.value }
    }

    private val configuredJson = Json { ignoreUnknownKeys = true }

    private val serializerNew: KSerializer<Option<RedisJsonValue<K, V>>> =
        object : KSerializer<Option<RedisJsonValue<K, V>>> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RedisJsonValue") {
                element("namespace", String.serializer().descriptor)
                element("key", kSerializer.descriptor)
                element(prefix, vSerializer.descriptor)
                element(existsField(prefix), String.serializer().descriptor)
            }

            override fun deserialize(decoder: Decoder): Option<RedisJsonValue<K, V>> =
                decoder.decodeStructure(descriptor) {
                    var namespace: String? = null
                    var key: K? = null
                    var value: V? = null
                    var exists: String? = null
                    while (true) {
                        when (val index = decodeElementIndex(descriptor)) {
                            0 -> namespace = decodeSerializableElement(descriptor, 0, String.serializer())
                            1 -> key = decodeSerializableElement(descriptor, 1, kSerializer)
                            2 -> value = decodeSerializableElement(descriptor, 2, vSerializer)
                            3 -> exists = decodeSerializableElement(descriptor, 3, String.serializer())
                            CompositeDecoder.DECODE_DONE -> break
                            else -> error("Unexpected index: $index")
                        }
                    }
                    nullable {
                        val gm: GameSessionId = namespace.bind().toIntOrNull().bind().let { GameSessionId(it) }
                        val ke: K = key.bind()
                        val valuee: V = value.bind()
                        val existsValue = exists.bind()
                        RedisJsonValue(gm, ke, valuee, existsValue)
                    }.toOption()
                }

            override fun serialize(encoder: Encoder, value: Option<RedisJsonValue<K, V>>) {
                value.map {
                    encoder.encodeStructure(descriptor) {
                        encodeSerializableElement(descriptor, 0, String.serializer(), it.namespace.value.toString())
                        encodeSerializableElement(descriptor, 1, kSerializer, it.key)
                        encodeSerializableElement(descriptor, 2, vSerializer, it.value)
                        encodeSerializableElement(descriptor, 3, String.serializer().nullable, it.exists)
                    }
                }
            }

        }

    suspend fun changeData(name: GameSessionId, key: K, value: V) {
        redisClient.jsonMerge(
            getName(name, key),
            "$",
            configuredJson.encodeToString(serializerNew, RedisJsonValue(name, key, value, "yes").some())
        ).toKotlin()
    }

    suspend fun removeElement(name: GameSessionId, key: K) {
        redisClient.jsonMerge(getName(name, key), "$", """{"${existsField(prefix)}":"nope"},"$prefix": null""")
            .toKotlin()
    }

    companion object {
        private val logger by LoggerDelegate()
        private const val maxPlayersInGame: Long = 100L
        private fun indexName(prefix: String): String = "$prefix-idx"
        private fun existsField(prefix: String): String = "${prefix}_exists"

        fun <K, V> createAsResource(
            redisCreationParams: RedisCreationParams<K, V>
        ): Resource<RedisJsonConnector<K, V>> = resource(
            acquire = {
                val prefix = redisCreationParams.prefix.prefix
                val (connection, client) = when (redisCreationParams.redisConfig.mode) {
                    RedisMode.CLUSTER -> {
                        val uris = redisCreationParams.redisConfig.hosts.map { config ->
                            RedisURI.create(config.host, config.port)
                        }
                        val clientCluster = RedisModulesClusterClient.create(uris)
                        val connectionCluster = clientCluster.connect()

                        connectionCluster to clientCluster
                    }

                    RedisMode.SINGLE_NODE -> {
                        val connectionConfig = redisCreationParams.redisConfig.hosts.first()
                        val client =
                            RedisModulesClient.create(RedisURI.create(connectionConfig.host, connectionConfig.port))
                        val connection = client.connect()

                        connection to client
                    }
                }
                Either.catch {
                    connection.sync().ftCreate(
                        indexName(prefix),
                        CreateOptions.builder<String, String>().prefix("player:").on(CreateOptions.DataType.JSON)
                            .build(),
                        TagField.Builder("$.namespace").`as`("namespace").build(),
                        TagField.Builder("\$.${existsField(prefix)}").`as`("check").build(),
                        NumericField.Builder("$.movementData.coords.x").`as`("x").build(),
                        NumericField.Builder("$.movementData.coords.y").`as`("y").build(),
                    )
                }.onLeft {
                    when {
                        (it is RedisCommandExecutionException) -> logger.error("Index creation thrown exception: ", it)
                        else -> throw it
                    }
                }

                connection to client
            },
            release = { resourceValue, _ ->
                val (connection, client) = resourceValue
                connection.close()
                client.shutdown()
            }
        ).map { (connection, _) ->
            RedisJsonConnector(
                redisCreationParams.prefix.prefix,
                redisCreationParams.kSerializer,
                redisCreationParams.vSerializer,
                connection.reactive()
            )
        }

    }

}
