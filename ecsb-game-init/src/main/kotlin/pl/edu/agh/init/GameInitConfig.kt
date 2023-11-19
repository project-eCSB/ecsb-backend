package pl.edu.agh.init

import pl.edu.agh.assets.domain.SavedAssetsConfig
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.assets.domain.GameAssets
import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.utils.HttpConfig

data class GameInitConfig(
    val httpConfig: HttpConfig,
    val jwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
    val gameToken: JWTConfig<Token.GAME_TOKEN>,
    val redis: RedisConfig,
    val savedAssets: SavedAssetsConfig,
    val defaultAssets: GameAssets,
    val rabbitConfig: RabbitConfig
)
