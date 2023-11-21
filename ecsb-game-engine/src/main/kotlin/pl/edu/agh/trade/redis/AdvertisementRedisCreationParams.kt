package pl.edu.agh.trade.redis

import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisCreationParams
import pl.edu.agh.trade.domain.AdvertiseDto

class AdvertisementRedisCreationParams(redisConfig: RedisConfig) : RedisCreationParams<PlayerId, AdvertiseDto>(
    redisConfig,
    "advertiseTrade",
    PlayerId.serializer(),
    AdvertiseDto.serializer()
)
