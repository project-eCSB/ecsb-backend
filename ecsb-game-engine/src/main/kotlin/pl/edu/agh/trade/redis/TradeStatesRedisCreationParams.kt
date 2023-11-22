package pl.edu.agh.trade.redis

import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisCreationParams
import pl.edu.agh.trade.domain.TradeStates

class TradeStatesRedisCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, TradeStates>(
    redisConfig,
    "tradeState",
    PlayerId.serializer(),
    TradeStates.serializer()
)
