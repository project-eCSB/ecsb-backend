package pl.edu.agh.engine

import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.redis.RedisConfig

data class GameEngineConfig(val redisConfig: RedisConfig, val rabbitConfig: RabbitConfig, val numOfThreads: Int)
