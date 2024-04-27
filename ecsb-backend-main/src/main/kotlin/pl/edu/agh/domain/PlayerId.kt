package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PlayerId(val value: String)

object PlayerIdConst {
    val MOVING_ID = PlayerId("ECSB_MOVING_PLAYER_ID")
    val CHAT_ID = PlayerId("ECSB_CHAT_PLAYER_ID")
    val TIMER_ID = PlayerId("ECSB_TIMER_PLAYER_ID")
    val COOP_ID = PlayerId("ECSB_COOP_PLAYER_ID")
    val TRADE_ID = PlayerId("ECSB_TRADE_PLAYER_ID")
}
