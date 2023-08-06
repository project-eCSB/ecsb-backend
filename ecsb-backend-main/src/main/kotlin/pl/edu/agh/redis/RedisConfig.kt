package pl.edu.agh.redis

data class RedisConfig(val mode: RedisMode, val hosts: List<RedisConnectionConfig>)

data class RedisConnectionConfig(val host: String, val port: Int)

enum class RedisMode {
    CLUSTER,
    SINGLE_NODE
}
