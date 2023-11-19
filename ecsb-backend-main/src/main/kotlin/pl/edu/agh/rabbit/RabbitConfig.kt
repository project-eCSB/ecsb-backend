package pl.edu.agh.rabbit

import pl.edu.agh.utils.Sensitive

data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: Sensitive,
    val vhost: String
)
