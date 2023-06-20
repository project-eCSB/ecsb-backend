package pl.edu.agh.coop.domain

import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.redis.RedisConfig

data class GameEngineConfig(val redis: RedisConfig, val rabbit: RabbitConfig)
