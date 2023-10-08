package pl.edu.agh.utils

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement

// Use with care (or don't use it at all)
class TimeTokenDecreaseStatement<A1, A2>(
    private val table: Table,
    where: Op<Boolean>,
    limit: Int,
    private val orderColumn: Column<*>,
    private val joinColumns: List<Column<*>>,
    private val updateObjects: List<Pair<Column<*>, Any?>>,
) : UpdateStatement(table.source, limit, where) {

    init {
        values.putAll(updateObjects)
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> =
        QueryBuilder(true).run {
            for ((key, value) in updateObjects) {
                registerArgument(key, value)
            }
            where?.toQueryBuilder(this)
            listOf(args)
        }

    override fun prepareSQL(transaction: Transaction): String =
        with(QueryBuilder(true)) {
            +"WITH random_alias_here as (select "
            joinColumns.appendTo(this, separator = ", ") {
                append(it)
            }
            +" from "
            +table.tableName
            +" where "
            +where!!
            +" order by "
            +orderColumn
            +" asc"
            +" limit "
            +(limit!!.toString())
            +" for update)"

            +" UPDATE "
            +table.tableName
            updateObjects.appendTo(this, prefix = " SET ") { (col, value) ->
                append("${transaction.identity(col)}=")
                registerArgument(col, value)
            }
            +" WHERE "
            joinColumns.appendTo(this, separator = ", ", prefix = "(", postfix = " )") {
                append(it)
            }
            +" IN (SELECT * FROM random_alias_here)"

            toString()
        }
}
