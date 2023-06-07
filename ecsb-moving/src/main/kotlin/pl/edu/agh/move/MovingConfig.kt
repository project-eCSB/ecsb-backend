package pl.edu.agh.move

import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.redis.RedisConfig

data class MovingConfig(
    val defaultAssets: GameAssets,
    val redis: RedisConfig,
    val jwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
    val gameToken: JWTConfig<Token.GAME_TOKEN>
)
