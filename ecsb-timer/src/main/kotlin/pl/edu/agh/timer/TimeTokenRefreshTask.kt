package pl.edu.agh.timer

import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TimeMessages
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.time.dao.PlayerTimeTokenDao
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.Transactor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class TimeTokenRefreshTask(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>
) {
    @OptIn(ExperimentalTime::class)
    suspend fun refreshTimeTokens() {
        flow { emit(1) }.repeat().metered(500.milliseconds).mapIndexed { _, _ ->
            Transactor.dbQuery { PlayerTimeTokenDao.getUpdatedTokens() }.map {
                it.forEach { (gameSessionId, tokens) ->
                    tokens.filterValues { timeTokens ->
                        timeTokens.filterValues { timeState -> timeState.isReady() }.isNotEmpty()
                    }.keys.forEach { playerId ->
                        equipmentChangeProducer.sendMessage(
                            gameSessionId,
                            playerId,
                            EquipmentInternalMessage.TimeTokenRegenerated
                        )
                    }
                    interactionProducer.sendMessage(
                        gameSessionId,
                        PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                        TimeMessages.TimeSystemOutputMessage.SessionPlayersTokensRefresh(tokens)
                    )
                }
            }
        }.collect()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun sendEndGame() =
        flow { emit(1) }.repeat().metered(500.milliseconds).mapIndexed { _, _ ->
            Transactor.dbQuery {
                val endedSomeTimeAgeGameSessions =
                    GameSessionDao.getGameSessionsEndedTimeAgo(TimestampMillis.ofMinutes(1))
                val justEndedGameSessions = GameSessionDao.endGameSessions()
                listOf(justEndedGameSessions, endedSomeTimeAgeGameSessions)
            }
                .flatten().forEach {
                    interactionProducer.sendMessage(
                        it,
                        PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                        TimeMessages.TimeSystemOutputMessage.GameTimeEnd
                    )
                }
        }.collect()

    @OptIn(ExperimentalTime::class)
    suspend fun refreshSessionTimes() = flow { emit(1) }.repeat().metered(10.seconds).mapIndexed { _, _ ->
        Transactor.dbQuery {
            GameSessionDao.getGameSessionTimes()
        }.map {
            it.forEach { (gameSessionId, timeLeft) ->
                interactionProducer.sendMessage(
                    gameSessionId,
                    PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                    TimeMessages.TimeSystemOutputMessage.GameTimeRemaining(timeLeft)
                )
            }
        }
    }.collect()

    private fun <T> Flow<T>.repeat(): Flow<T> =
        flow {
            while (true) {
                collect {
                    emit(it)
                }
            }
        }
}
