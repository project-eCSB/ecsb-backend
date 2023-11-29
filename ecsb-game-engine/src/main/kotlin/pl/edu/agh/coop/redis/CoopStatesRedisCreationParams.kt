package pl.edu.agh.coop.redis

import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisCreationParams

class CoopStatesRedisCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, CoopStates>(
    redisConfig,
    "coopState",
    PlayerId.serializer(),
    CoopStates.serializer()
)
