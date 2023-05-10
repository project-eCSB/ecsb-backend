package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable

@Serializable
data class SessionClassDto(val assetNumber: AssetNumber, val resourceName: String)
