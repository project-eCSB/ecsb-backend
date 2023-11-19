package pl.edu.agh.auth.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object RoleTable : Table("ROLE") {
    val roleId: Column<Int> = integer("ID")
    val name: Column<String> = varchar("NAME", 256)
    override val primaryKey = PrimaryKey(roleId)
}
