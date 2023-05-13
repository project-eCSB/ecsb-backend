package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Password(val value: String) {
    override fun toString(): String {
        return "***"
    }
}
