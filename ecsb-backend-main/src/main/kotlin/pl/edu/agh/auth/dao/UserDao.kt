package pl.edu.agh.auth.dao

import arrow.core.Option
import arrow.core.singleOrNone
import org.jetbrains.exposed.sql.*
import pl.edu.agh.auth.domain.*
import pl.edu.agh.auth.table.RoleTable
import pl.edu.agh.auth.table.UserRolesTable
import pl.edu.agh.auth.table.UserTable
import pl.edu.agh.utils.PGCryptoUtils.selectEncryptedPassword
import pl.edu.agh.utils.PGCryptoUtils.toDbValue

object UserDao {

    fun insertNewUser(loginCredentials: LoginCredentials): LoginUserId = UserTable.insert {
        it[email] = loginCredentials.email
        it[password] = loginCredentials.password.toDbValue()
    }[UserTable.id]

    fun findUserByEmail(email: String): Option<LoginUserDTO> =
        UserTable.select { UserTable.email eq email }.singleOrNone().map { UserTable.toDomain(it) }

    fun verifyCredentials(email: String, password: Password): Option<LoginUserDTO> =
        UserTable.select { UserTable.email eq email and (UserTable.password.selectEncryptedPassword(password)) }
            .singleOrNone()
            .map { UserTable.toDomain(it) }

    fun getUserRoles(userId: LoginUserId): List<Role> =
        RoleTable.join(UserRolesTable, JoinType.INNER, additionalConstraint = {
            UserRolesTable.roleId eq RoleTable.roleId
        }).select { UserRolesTable.userId eq userId }.map { Role.fromId(it[RoleTable.roleId]) }
}
