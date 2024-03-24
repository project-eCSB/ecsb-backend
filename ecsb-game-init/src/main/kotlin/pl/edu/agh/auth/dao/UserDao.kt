package pl.edu.agh.auth.dao

import arrow.core.Option
import arrow.core.singleOrNone
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.input.LoginRequest
import pl.edu.agh.auth.domain.output.LoginUserDto
import pl.edu.agh.auth.table.RoleTable
import pl.edu.agh.auth.table.UserRolesTable
import pl.edu.agh.auth.table.UserTable
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.utils.PGCryptoUtils.selectEncryptedPassword
import pl.edu.agh.utils.PGCryptoUtils.toDbValue
import pl.edu.agh.utils.Sensitive
import pl.edu.agh.utils.toDomain

object UserDao {

    fun insertNewUser(loginRequest: LoginRequest): LoginUserId = UserTable.insert {
        it[UserTable.email] = loginRequest.email
        it[UserTable.password] = loginRequest.password.toDbValue()
    }[UserTable.id]

    fun findUserByEmail(email: String): Option<LoginUserDto> =
        UserTable.select { UserTable.email eq email }.toDomain(UserTable).singleOrNone()

    fun verifyCredentials(email: String, password: Sensitive): Option<LoginUserDto> =
        UserTable
            .select { UserTable.email eq email and (UserTable.password.selectEncryptedPassword(password)) }
            .toDomain(UserTable)
            .singleOrNone()

    fun getUserRoles(userId: LoginUserId): List<Role> =
        RoleTable.join(UserRolesTable, JoinType.INNER, additionalConstraint = {
            UserRolesTable.roleId eq RoleTable.roleId
        }).slice(RoleTable.roleId).select { UserRolesTable.userId eq userId }.map { Role.fromId(it[RoleTable.roleId]) }
}
