package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PlayerId(val value: String)
