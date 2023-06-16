package pl.edu.agh.rabbit

import pl.edu.agh.auth.domain.Password

data class RabbitConfig(val port: Int, val host: String, val password: Password, val username: String)
