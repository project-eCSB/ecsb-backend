package pl.edu.agh.auth.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.auth.domain.LoginUserDTO
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.loginUserId
import pl.edu.agh.utils.Domainable

object UserTable : Table("LOGIN_USER"), Domainable<LoginUserDTO> {
    val id: Column<LoginUserId> = loginUserId("ID").autoIncrement()
    val email: Column<String> = varchar("LOGIN", 256)
    val password: Column<String> = varchar("PASSWORD", 256)

    override fun toDomain(resultRow: ResultRow): LoginUserDTO = LoginUserDTO(
        id = resultRow[id],
        email = resultRow[email]
    )

    override val domainColumns: List<Expression<*>> = listOf(id, email)
}
