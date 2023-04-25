package pl.edu.agh.redis

import io.ktor.server.application.*
import pl.edu.agh.auth.service.getConfigProperty

data class RedisConfig(val host: String, val port: Int)

fun Application.getRedisConfig(): RedisConfig {
    val redisHost = getConfigProperty("redis.host")
    val redisPort = getConfigProperty("redis.port").toInt()

    return RedisConfig(redisHost, redisPort)
}
