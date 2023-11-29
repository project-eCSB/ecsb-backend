package pl.edu.agh.utils

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Sensitive(val value: String) {
    override fun toString(): String {
        return "***"
    }
}
