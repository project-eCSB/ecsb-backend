package pl.edu.agh.auth.domain

import arrow.core.Option
import arrow.core.some
import kotlinx.serialization.Serializable

@Serializable
enum class Roles(val id: Int, val roleName: String) {
    USER(1, "USER"),
    ADMIN(2, "ADMIN");
    companion object {
        fun fromId(id: Int?): Roles =
            when (id) {
                1 -> USER
                2 -> ADMIN
                else -> throw IllegalArgumentException("Unknown role id: $id")
            }
    }
}