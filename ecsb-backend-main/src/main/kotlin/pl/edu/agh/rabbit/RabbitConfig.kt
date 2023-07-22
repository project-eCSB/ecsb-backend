package pl.edu.agh.rabbit

import pl.edu.agh.auth.domain.Password

data class RabbitConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: Password,
    val vhost: String
)
