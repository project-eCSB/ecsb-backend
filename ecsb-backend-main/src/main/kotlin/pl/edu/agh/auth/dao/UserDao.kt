package pl.edu.agh.auth.dao

import arrow.core.Option
import arrow.core.singleOrNone
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.auth.domain.LoginCredentials
import pl.edu.agh.auth.domain.LoginUserDTO
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.table.RoleTable
import pl.edu.agh.auth.table.UserRolesTable
import pl.edu.agh.auth.table.UserTable

object UserDao {

    fun insertNewUser(loginCredentials: LoginCredentials): LoginUserId = UserTable.insert {
        it[email] = loginCredentials.email
        it[password] = loginCredentials.password
    }[UserTable.id]

    fun findUserByEmail(email: String): Option<LoginUserDTO> =
        UserTable.select { UserTable.email eq email }.singleOrNone().map { UserTable.toDomain(it) }

    fun verifyCredentials(email: String, password: String): Option<LoginUserDTO> =
        UserTable.select { UserTable.email eq email and (UserTable.password eq password) }.singleOrNone()
            .map { UserTable.toDomain(it) }

    fun getUserRoles(userId: LoginUserId): List<Role> =
        RoleTable.join(UserRolesTable, JoinType.INNER, additionalConstraint = {
            UserRolesTable.roleId eq RoleTable.roleId
        }).select { UserRolesTable.userId eq userId }.map { Role.fromId(it[RoleTable.roleId]) }
}
