package pl.edu.agh.coop.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.AmountDiff
import pl.edu.agh.utils.NonNegInt

/**
 * @param time - diff of token amount not internal value of each token
 */
@Serializable
data class TimeTokensCoopInfo(val time: AmountDiff<NonNegInt>)
