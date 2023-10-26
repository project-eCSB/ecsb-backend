package pl.edu.agh.utils

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import pl.edu.agh.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.table.TimeTokensUsedInfo
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos

// Use with care (or don't use it at all)
class TimeTokenDecreaseStatement<A1, A2>(
    private val table: Table,
    val where: Op<Boolean>,
    val limit: Int,
    private val orderColumn: Column<*>,
    private val joinColumns: List<Column<*>>,
    private val updateObjects: List<Pair<Column<*>, Any?>>,
) : UpdateBuilder<TimeTokensUsedInfo>(StatementType.SELECT, table.source.targetTables()) {

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): TimeTokensUsedInfo {
        return executeQuery().let { rs ->
            val usedTokenInfo = mutableMapOf<TimeTokenIndex, TimeState>()
            while (rs.next()) {
                val index = rs.getInt("INDEX").let { TimeTokenIndex(it) }
                val state = rs.getInt("MAX_STATE").let { TimeState(actual = 0.nonNeg, max = it.pos) }

                usedTokenInfo[index] = state
            }

            return@let TimeTokensUsedInfo(amountUsed = usedTokenInfo.size.nonNeg, usedTokenInfo.toNonEmptyMapOrNone())
        }
    }

    init {
        values.putAll(updateObjects)
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> =
        QueryBuilder(true).run {
            for ((key, value) in updateObjects) {
                registerArgument(key, value)
            }
            where.toQueryBuilder(this)
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
            +where
            +" order by "
            +orderColumn
            +" asc"
            +" limit "
            +(limit.toString())
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
            +"RETURNING PLAYER_TIME_TOKEN.INDEX, PLAYER_TIME_TOKEN.MAX_STATE"

            toString()
        }
}
