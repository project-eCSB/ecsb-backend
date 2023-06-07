package pl.edu.agh.chat

import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.utils.HttpConfig

data class ChatConfig(
    val httpConfig: HttpConfig,
    val jwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
    val gameToken: JWTConfig<Token.GAME_TOKEN>,
    val redis: RedisConfig
)
