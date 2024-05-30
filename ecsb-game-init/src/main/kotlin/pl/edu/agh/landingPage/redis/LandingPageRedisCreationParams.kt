package pl.edu.agh.landingPage.redis

import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisCreationParams

class LandingPageRedisCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, PlayerId>(
    redisConfig,
    "landingPage",
    PlayerId.serializer(),
    PlayerId.serializer()
)
