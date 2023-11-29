package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PlayerId(val value: String)

object PlayerIdConst {
    val ECSB_MOVING_PLAYER_ID = PlayerId("ECSB_MOVING_PLAYER_ID")
    val ECSB_CHAT_PLAYER_ID = PlayerId("ECSB_CHAT_PLAYER_ID")
    val ECSB_TIMER_PLAYER_ID = PlayerId("ECSB_TIMER_PLAYER_ID")
    val ECSB_COOP_PLAYER_ID = PlayerId("ECSB_COOP_PLAYER_ID")
    val ECSB_TRADE_PLAYER_ID = PlayerId("ECSB_TRADE_PLAYER_ID")
}
