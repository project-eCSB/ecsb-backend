package pl.edu.agh.time.domain

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.LongColumnType
import pl.edu.agh.utils.BaseDBWrapper

@JvmInline
@Serializable
value class TimestampMillis(val value: Long = 0L) {
    companion object {
        val columnType = BaseDBWrapper(LongColumnType(), TimestampMillis::value, ::TimestampMillis)

        fun ofMinutes(minutes: Long): TimestampMillis = TimestampMillis(minutes * 60 * 1000)
    }
}
