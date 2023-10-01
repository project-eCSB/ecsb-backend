package pl.edu.agh.time.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class TimeTokenIndex(val index: Int)
