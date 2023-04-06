package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Role(val roleId: Int, val roleName: String) {
    USER(1, "USER"),
    ADMIN(2, "ADMIN");

    companion object {
        private val idToRoleMap: Map<Int, Role> = values().associateBy(Role::roleId)
        fun fromId(roleId: Int): Role =
            idToRoleMap[roleId] ?: throw IllegalArgumentException("Unknown role id: $roleId")
    }
}
