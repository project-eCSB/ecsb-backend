package pl.edu.agh.timer

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.mapIndexed
import arrow.fx.coroutines.metered
import arrow.fx.coroutines.resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.TimeMessages
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.Transactor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

class TimeTokenRefreshTask(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>
) {
    @OptIn(ExperimentalTime::class)
    suspend fun refreshTimeTokens(): Resource<Unit> = resource {
        flow { emit(1) }.repeat().metered(500.milliseconds).mapIndexed { _, _ ->
            Transactor.dbQuery {
                PlayerTimeTokenDao.getUpdatedTokens().map {
                    it.forEach { (gameSessionId, tokens) ->
                        interactionProducer.sendMessage(
                            gameSessionId,
                            PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                            TimeMessages.TimeSystemOutputMessage.SessionPlayersTokensRefresh(tokens)
                        )
                    }
                }
            }
        }.collect()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun refreshSessionTimes(): Resource<Unit> = resource {
        flow { emit(1) }.repeat().metered(500.milliseconds).mapIndexed { _, _ ->
            Transactor.dbQuery {
                GameSessionDao.endGameSessions().forEach {
                    interactionProducer.sendMessage(
                        it,
                        PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                        TimeMessages.TimeSystemOutputMessage.GameTimeEnd
                    )
                }
            }
            Transactor.dbQuery {
                GameSessionDao.getGameSessionTimes().map {
                    it.forEach { (gameSessionId, timeLeft) ->
                        interactionProducer.sendMessage(
                            gameSessionId,
                            PlayerIdConst.ECSB_TIMER_PLAYER_ID,
                            TimeMessages.TimeSystemOutputMessage.GameTimeRemaining(timeLeft)
                        )
                    }
                }
            }
        }.collect()
    }

    private fun <T> Flow<T>.repeat(): Flow<T> =
        flow {
            while (true) {
                collect {
                    emit(it)
                }
            }
        }
}
