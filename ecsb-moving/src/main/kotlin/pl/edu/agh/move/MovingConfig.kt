package pl.edu.agh.move

import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.utils.HttpConfig

data class MovingConfig(
    val httpConfig: HttpConfig,
    val redis: RedisConfig,
    val jwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
    val gameToken: JWTConfig<Token.GAME_TOKEN>,
    val rabbitConfig: RabbitConfig
)
