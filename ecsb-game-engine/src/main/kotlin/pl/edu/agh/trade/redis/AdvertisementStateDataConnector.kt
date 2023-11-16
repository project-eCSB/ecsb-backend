package pl.edu.agh.trade.redis

import arrow.core.getOrElse
import arrow.core.none
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.trade.domain.AdvertiseDto

interface AdvertisementStateDataConnector {
    suspend fun getPlayerStates(gameSessionId: GameSessionId): Map<PlayerId, AdvertiseDto>
    suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): AdvertiseDto
    suspend fun setPlayerState(gameSessionId: GameSessionId, playerId: PlayerId, newPlayerStatus: AdvertiseDto)
}

class AdvertisementStateDataConnectorImpl(
    private val redisHashMapConnector: RedisJsonConnector<PlayerId, AdvertiseDto>
) : AdvertisementStateDataConnector {
    override suspend fun getPlayerStates(gameSessionId: GameSessionId): Map<PlayerId, AdvertiseDto> =
        redisHashMapConnector.getAll(gameSessionId)

    override suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): AdvertiseDto =
        redisHashMapConnector.findOne(gameSessionId, playerId).getOrElse { AdvertiseDto(none(), none()) }

    override suspend fun setPlayerState(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        newPlayerStatus: AdvertiseDto
    ) {
        if (newPlayerStatus.isEmpty()) {
            redisHashMapConnector.removeElement(gameSessionId, playerId)
        } else {
            redisHashMapConnector.changeData(gameSessionId, playerId, newPlayerStatus)
        }
    }
}
