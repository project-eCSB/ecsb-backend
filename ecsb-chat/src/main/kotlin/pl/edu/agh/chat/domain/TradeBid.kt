package pl.edu.agh.chat.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerEquipment

@Serializable
data class TradeBid(val senderOffer: PlayerEquipment, val senderRequest: PlayerEquipment)
