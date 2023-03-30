package pl.edu.agh.auth.table

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import pl.edu.agh.auth.domain.LoginUserDTO
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.loginUserId

object UserTable : IdTable<LoginUserId>("LOGIN_USER") {
    override val id: Column<EntityID<LoginUserId>> = loginUserId("ID").autoIncrement().entityId()
    val email: Column<String> = varchar("LOGIN", 256)
    val password: Column<String> = varchar("PASSWORD", 256)

    fun toDomain(it: ResultRow): LoginUserDTO =
        LoginUserDTO(
            id = it[id].value, email = it[email]
        )
}

