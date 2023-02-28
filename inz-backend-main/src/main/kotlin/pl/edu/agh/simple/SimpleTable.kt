package pl.edu.agh.simple

import arrow.core.Option
import arrow.core.none
import arrow.core.singleOrNone
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object SimpleTable {
    @Serializable
    data class SimpleTableDTO(val id: Int, val name: String)

    object SimpleTable : Table() {
        val id: Column<Int> = integer("id").autoIncrement()
        val name: Column<String> = varchar("name", 50)
        override val primaryKey = PrimaryKey(id)

        fun toDomain(it: ResultRow): Option<SimpleTableDTO> = Option.catch(recover = {
            println("Error while converting to domain: $it")
            none()
        }) {
            SimpleTableDTO(
                id = it[id], name = it[name]
            )
        }
    }

    fun create() = transaction { SchemaUtils.create(SimpleTable) }


    fun getById(id: Int): Option<SimpleTableDTO> {
        return SimpleTable.select { SimpleTable.id eq id }.singleOrNone().flatMap { SimpleTable.toDomain(it) }
    }
}
