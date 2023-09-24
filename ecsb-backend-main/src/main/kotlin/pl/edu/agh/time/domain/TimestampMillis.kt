package pl.edu.agh.time.domain

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class TimestampMillis(val value: Long = 0L)