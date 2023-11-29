package pl.edu.agh.moving.redis

import pl.edu.agh.domain.PlayerId
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisCreationParams

class MovementRedisCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, PlayerPosition>(
    redisConfig,
    "movementData",
    PlayerId.serializer(),
    PlayerPosition.serializer()
)
