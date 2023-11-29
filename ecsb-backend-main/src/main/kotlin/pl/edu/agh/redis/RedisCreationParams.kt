package pl.edu.agh.redis

import kotlinx.serialization.KSerializer

abstract class RedisCreationParams<K, V>(
    val redisConfig: RedisConfig,
    val prefix: String,
    val kSerializer: KSerializer<K>,
    val vSerializer: KSerializer<V>
)
