package pl.edu.agh.engine

import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.redis.RedisConfig

data class GameEngineConfig(val redis: RedisConfig, val rabbit: RabbitConfig, val numOfThreads: Int)
