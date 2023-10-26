package pl.edu.agh.utils

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow

interface Domainable<T> {
    val domainColumns: List<Expression<*>>

    fun toDomain(resultRow: ResultRow): T
}

fun <T> Query.toDomain(domainable: Domainable<T>): List<T> =
    this.adjustSlice { slice(domainable.domainColumns) }.map(domainable::toDomain)
