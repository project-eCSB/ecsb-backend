package pl.edu.agh.travel.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class TravelId(val value: Int)
