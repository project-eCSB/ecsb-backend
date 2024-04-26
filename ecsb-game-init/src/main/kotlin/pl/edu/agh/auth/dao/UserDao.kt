package pl.edu.agh.auth.dao

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.singleOrNone
import org.jetbrains.exposed.sql.*
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
import pl.edu.agh.utils.UpdateObject
import pl.edu.agh.utils.toDomain
import pl.edu.agh.utils.updateReturning
import java.time.Instant

object UserDao {

    fun insertNewUser(loginRequest: LoginRequest, verificationToken: String): LoginUserId = UserTable.insert {
        it[UserTable.email] = loginRequest.email
        it[UserTable.password] = loginRequest.password.toDbValue()
        it[UserTable.verified] = false
        it[UserTable.verificationToken] = verificationToken
        it[UserTable.alterDate] = Instant.now()
    }[UserTable.id]

    fun verifyUser(email: String, verificationToken: String): Option<LoginUserId> = UserTable.updateReturning(
        where = { (UserTable.email eq email) and (UserTable.verificationToken eq verificationToken) },
        from = UserTable.alias("old_login_user"),
        joinColumns = listOf(UserTable.id),
        updateObjects = UpdateObject(UserTable.verified, Op.TRUE),
        returningNew = mapOf("id" to UserTable.id)
    ).map { (it.returningNew["id"] as LoginUserId) }.firstOrNone()

    fun findUserByEmail(email: String): Option<LoginUserDto> =
        UserTable.select { UserTable.email eq email and UserTable.verified }.toDomain(UserTable).singleOrNone()

    fun verifyCredentials(email: String, password: Sensitive): Option<LoginUserDto> =
        UserTable
            .select { UserTable.email eq email and (UserTable.password.selectEncryptedPassword(password)) and UserTable.verified }
            .toDomain(UserTable)
            .singleOrNone()

    fun getUserRoles(userId: LoginUserId): List<Role> =
        RoleTable.join(UserRolesTable, JoinType.INNER, additionalConstraint = {
            UserRolesTable.roleId eq RoleTable.roleId
        }).slice(RoleTable.roleId).select { UserRolesTable.userId eq userId }.map { Role.fromId(it[RoleTable.roleId]) }
}
