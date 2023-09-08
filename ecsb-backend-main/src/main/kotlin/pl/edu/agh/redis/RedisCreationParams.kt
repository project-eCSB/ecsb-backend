package pl.edu.agh.redis

import kotlinx.serialization.KSerializer
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.trade.domain.TradeStates

sealed class RedisCreationParams<K, V>(
    val redisConfig: RedisConfig,
    val prefix: RedisPrefixes,
    val kSerializer: KSerializer<K>,
    val vSerializer: KSerializer<V>
)

class CoopStatesCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, CoopStates>(
    redisConfig,
    RedisPrefixes.COOP_STATE,
    PlayerId.serializer(),
    CoopStates.serializer()
)

class TradeStatesCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, TradeStates>(
    redisConfig,
    RedisPrefixes.TRADE_STATE,
    PlayerId.serializer(),
    TradeStates.serializer()
)

class MovementCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, PlayerPosition>(
    redisConfig,
    RedisPrefixes.MOVEMENT_DATA,
    PlayerId.serializer(),
    PlayerPosition.serializer()
)

enum class RedisPrefixes(val prefix: String) {
    COOP_STATE("coopState"),
    TRADE_STATE("tradeState"),
    MOVEMENT_DATA("movementData")
}

