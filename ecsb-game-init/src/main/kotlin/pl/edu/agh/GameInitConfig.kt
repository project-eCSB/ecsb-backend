package pl.edu.agh

import pl.edu.agh.assets.domain.SavedAssetsConfig
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.redis.RedisConfig

data class GameInitConfig(val jwt: JWTConfig<Token.LOGIN_USER_TOKEN>, val gameToken: JWTConfig<Token.GAME_TOKEN>, val redis: RedisConfig, val savedAssets: SavedAssetsConfig, val defaultAssets: GameAssets)