package pl.edu.agh.auth.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.auth.domain.LoginUserDTO
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.loginUserId

object UserTable : Table("LOGIN_USER") {
    val id: Column<LoginUserId> = loginUserId("ID").autoIncrement()
    val email: Column<String> = varchar("LOGIN", 256)
    val password: Column<String> = varchar("PASSWORD", 256)

    fun toDomain(it: ResultRow): LoginUserDTO =
        LoginUserDTO(
            id = it[id],
            email = it[email]
        )
}
