package pl.edu.agh.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Table

@Serializable(with = GenericIntIdSerializer::class)
abstract class GenericIntId<T> {
    abstract val id: Int
}

abstract class GenericIntIdFactory<T : GenericIntId<T>> {
    abstract fun create(id: Int): T
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
