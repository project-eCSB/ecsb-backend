package pl.edu.agh.trade.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.utils.OptionS

@Serializable
data class AdvertiseDto(val buy: OptionS<GameResourceName>, val sell: OptionS<GameResourceName>) {
    fun isEmpty(): Boolean = buy.isNone() && sell.isNone()
}
