package pl.edu.agh.moving.redis

import pl.edu.agh.domain.PlayerId
import pl.edu.agh.moving.PlayerPositionDto
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisCreationParams

class MovementRedisCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, PlayerPositionDto>(
    redisConfig,
    "movementData",
    PlayerId.serializer(),
    PlayerPositionDto.serializer()
)

