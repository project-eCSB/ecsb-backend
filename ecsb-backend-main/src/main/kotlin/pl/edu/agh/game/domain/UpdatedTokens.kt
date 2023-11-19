package pl.edu.agh.game.domain

import arrow.core.none
import kotlinx.serialization.Serializable
import pl.edu.agh.time.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.OptionS

@Serializable
data class UpdatedTokens(val timeTokensUsed: OptionS<NonEmptyMap<TimeTokenIndex, TimeState>>) {
    companion object {
        val empty: UpdatedTokens = UpdatedTokens(none())
    }
}
