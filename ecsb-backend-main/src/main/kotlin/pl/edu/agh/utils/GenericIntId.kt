package pl.edu.agh.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.*

@Serializable(with = GenericIntIdSerializer::class)
interface GenericIntId<T> {
    val id: Int
}

interface GenericIntIdFactory<T : GenericIntId<T>> {
    fun create(id: Int): T
}

abstract class GenericIntIdSerializer<T : GenericIntId<T>>(private val factory: GenericIntIdFactory<T>) :
    KSerializer<T> {

    override fun deserialize(decoder: Decoder): T = factory.create(decoder.decodeInt())

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(value.id)
    }
}

class GenericIntIdColumnType<T : GenericIntId<T>>(private val factory: GenericIntIdFactory<T>) : ColumnType() {
    override var nullable: Boolean = false

    override fun sqlType(): String = IntegerColumnType().sqlType()

    override fun valueFromDB(value: Any): T {
        return when (value) {
            is Int -> factory.create(value)
            is Number -> factory.create(value.toInt())
            is String -> factory.create(value.toInt())
            else -> error("Unexpected value of type Int: $value of ${value::class.qualifiedName}")
        }
    }

    override fun valueToDB(value: Any?): Any? {
        if (value is GenericIntId<*>) {
            return value.id
        }
        return null
    }
}

fun <T : GenericIntId<T>> Table.genericIntId(factory: GenericIntIdFactory<T>): (String) -> Column<T> = {
    registerColumn(it, GenericIntIdColumnType(factory))
}

@Suppress("UNCHECKED_CAST")
class BaseDBWrapper<K, T : Any>(
    val baseColumnType: ColumnType,
    val toDB: (T) -> K,
    val fromDB: (K) -> T
) : ColumnType() {
    override fun sqlType(): String = baseColumnType.sqlType()
    override fun valueFromDB(value: Any): T = fromDB(baseColumnType.valueFromDB(value) as K)
    override fun valueToDB(value: Any?): Any? = baseColumnType.valueToDB(value)?.let { toDB(value as T) }
}

fun <T : Any> Table.floatWrapper(toDB: (T) -> Float, fromDB: (Float) -> T): (String) -> Column<T> = {
    registerColumn(it, BaseDBWrapper(FloatColumnType(), toDB, fromDB))
}

fun <T : Any> Table.intWrapper(toDB: (T) -> Int, fromDB: (Int) -> T): (String) -> Column<T> = {
    registerColumn(it, BaseDBWrapper(IntegerColumnType(), toDB, fromDB))
}

fun <T : Any> Table.longWrapper(toDB: (T) -> Long, fromDB: (Long) -> T): (String) -> Column<T> = {
    registerColumn(it, BaseDBWrapper(LongColumnType(), toDB, fromDB))
}

fun <T : Any> Table.stringWrapper(toDB: (T) -> String, fromDB: (String) -> T): (String) -> Column<T> = {
    registerColumn(it, BaseDBWrapper(VarCharColumnType(), toDB, fromDB))
}

fun <T : Any> Table.nullableStringWrapper(toDB: (T) -> String?, fromDB: (String?) -> T): (String) -> Column<T> = {
    registerColumn(it, BaseDBWrapper(VarCharColumnType(), toDB, fromDB))
}

fun String.toPgJson(): Expression<String> = Expression.build {
    PGJsonCast(this@toPgJson)
}

class PGJsonCast(val expr: String) : Expression<String>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append('\'', expr, '\'')
        append("::json")
    }
}
