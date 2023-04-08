package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PlayerId(val value: String)


object PlayerIdConst {
    val ECSB_MOVING_PLAYER_ID = PlayerId("ECSB_MOVING_PLAYER_ID")
}