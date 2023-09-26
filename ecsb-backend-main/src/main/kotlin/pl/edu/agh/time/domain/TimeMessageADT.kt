package pl.edu.agh.time.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.NonNegInt

@Serializable
sealed interface TimeMessageADT

@Serializable
data class GameEndEvent(val gameSessionId: Int) : TimeMessageADT

@Serializable
data class ReplenishedTimeEvent(val gameSessionId: Int, val playerId: PlayerId, val timeAmountToReplenish: NonNegInt) :
    TimeMessageADT
