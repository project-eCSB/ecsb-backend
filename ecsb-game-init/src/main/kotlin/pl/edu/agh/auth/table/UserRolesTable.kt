package pl.edu.agh.auth.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.loginUserId

object UserRolesTable : Table("LOGIN_USER_ROLE") {
    val userId: Column<LoginUserId> = loginUserId("LOGIN_USER_ID")
    val roleId: Column<Int> = integer("ROLE_ID")
}
