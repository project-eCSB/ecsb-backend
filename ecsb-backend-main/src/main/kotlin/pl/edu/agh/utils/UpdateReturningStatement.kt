package pl.edu.agh.utils

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.StatementType

operator fun FieldSet.plus(slice: FieldSet): FieldSet {
    return Slice(this.source, this.fields + slice.fields)
}

class UpdateReturningStatement<S>(
    private val table: Table,
    private val where: Op<Boolean>? = null,
    private val from: Alias<Table>,
    private val limit: Int? = null,
    private val returning: Map<String, Column<S>>,
    private val returningNew: Map<String, Column<*>>
) : ReturningStatement(StatementType.UPDATE, listOf(table)) {
    override val set: FieldSet =
        (
            table.slice(returning.map { (key, value) -> value.alias("new_$key") }) + from.slice(
                returning.map { (key, value) ->
                    from[value].alias(
                        "old_$key"
                    )
                }
            )
            ) + table.slice(returningNew.map { (key, value) -> value.alias(key) })

    private val firstDataSet: List<Pair<Column<*>, Any?>>
        get() = values.toList()

    override fun prepareSQL(transaction: Transaction): String =
        with(QueryBuilder(true)) {
            +"UPDATE "
            table.describe(transaction, this)

            firstDataSet.appendTo(this, prefix = " SET ") { (col, value) ->
                append("${transaction.identity(col)}=")
                registerArgument(col, value)
            }

            +" FROM "
            +from.tableNameWithAlias

            where?.let {
                +" WHERE "
                +it
            }
            limit?.let {
                +" LIMIT "
                +it
            }

            +" RETURNING "
            set.fields.appendTo(this, separator = ", ") {
                append(it)
            }

            toString()
        }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> =
        QueryBuilder(true).run {
            for ((key, value) in values) {
                registerArgument(key, value)
            }
            where?.toQueryBuilder(this)
            listOf(args)
        }

    // region UpdateBuilder
    private val values: MutableMap<Column<*>, Any?> = LinkedHashMap()

    operator fun <S> set(column: Column<S>, value: S) {
        when {
            values.containsKey(column) -> error("$column is already initialized")
            !column.columnType.nullable && value == null -> error("Trying to set null to not nullable column $column")
            else -> values[column] = value
        }
    }

    @JvmName("setWithEntityIdExpression")
    operator fun <S, ID : EntityID<S>, E : Expression<S>> set(
        column: Column<ID>,
        value: E
    ) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = value
    }

    @JvmName("setWithEntityIdValue")
    operator fun <S : Comparable<S>, ID : EntityID<S>, E : S?> set(
        column: Column<ID>,
        value: E
    ) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = value
    }

    operator fun <T, S : T, E : Expression<S>> set(column: Column<T>, value: E) =
        update(column, value)

    operator fun <S> set(column: CompositeColumn<S>, value: S) {
        @Suppress("UNCHECKED_CAST")
        column.getRealColumnsWithValues(value).forEach { (realColumn, itsValue) ->
            set(
                realColumn as Column<Any?>,
                itsValue
            )
        }
    }

    fun <T, S : T?> update(column: Column<T>, value: Expression<S>) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = value
    }

    fun <T, S : T?> update(
        column: Column<T>,
        value: SqlExpressionBuilder.() -> Expression<S>
    ) {
        require(!values.containsKey(column)) { "$column is already initialized" }
        values[column] = SqlExpressionBuilder.value()
    }
    // endregion
}

data class ValueChanges<S>(val before: S, val after: S)

data class ReturningObject<K, S>(
    val returningNew: Map<String, K>,
    val returningBoth: Map<String, ValueChanges<S>>
)

data class UpdateObject<T>(
    val column: Column<T>,
    val expression: Expression<T>
)

fun <T : Table, K, S> T.updateReturning(
    where: SqlExpressionBuilder.() -> Op<Boolean>,
    limit: Int? = null,
    from: Alias<T>,
    joinColumns: List<Column<*>>,
    updateObjects: List<UpdateObject<S>>,
    returningNew: Map<String, Column<K>> = mapOf()
): List<ReturningObject<K, S>> = UpdateReturningStatement(
    this,
    joinColumns.fold(SqlExpressionBuilder.run(where)) { actualWhere, column -> actualWhere.and(from[column] eq column) },
    from,
    limit,
    updateObjects.associate { it.column.name to it.column },
    returningNew
).apply {
    updateObjects.forEach { updateObject ->
        this@apply.update(
            updateObject.column,
            updateObject.expression
        )
    }
    exec()
}.map {
    val returningBoth2 = updateObjects.associate { (key, _) ->
        key.name to ValueChanges(after = it[key.alias("new_${key.name}")], before = it[from[key].alias("old_${key.name}")])
    }
    val returningNew2 = returningNew.map { (key, value) ->
        key to it[value.alias(key)]
    }.toMap()
    ReturningObject<K, S>(returningNew2, returningBoth2)
}
