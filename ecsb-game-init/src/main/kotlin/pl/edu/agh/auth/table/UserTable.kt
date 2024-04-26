package pl.edu.agh.auth.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.auth.domain.output.LoginUserDto
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.loginUserId
import pl.edu.agh.utils.Domainable
import pl.edu.agh.utils.timestampWithTimeZone
import java.time.Instant

object UserTable : Table("LOGIN_USER"), Domainable<LoginUserDto> {
    val id: Column<LoginUserId> = loginUserId("ID").autoIncrement()
    val email: Column<String> = varchar("LOGIN", 256)
    val password: Column<String> = varchar("PASSWORD", 256)
    val verificationToken: Column<String> = varchar("VERIFICATION_TOKEN", 255)
    val alterDate: Column<Instant> = timestampWithTimeZone("ALTER_DATE")
    val verified: Column<Boolean> = bool("VERIFIED")

    override fun toDomain(resultRow: ResultRow): LoginUserDto = LoginUserDto(
        id = resultRow[id],
        email = resultRow[email]
    )

    override val domainColumns: List<Expression<*>> = listOf(id, email)
}
