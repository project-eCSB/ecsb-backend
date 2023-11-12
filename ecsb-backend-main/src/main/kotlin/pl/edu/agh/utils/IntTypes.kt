package pl.edu.agh.utils

import arrow.core.Option
import arrow.core.getOrElse
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

@JvmInline
@Serializable
value class PosInt(val value: Int) : Comparable<PosInt> {
    init {
        require(value > 0)
    }

    fun toNonNeg(): NonNegInt = NonNegInt(value)

    operator fun times(other: PosInt): PosInt =
        PosInt(other.value * value)

    operator fun minus(other: PosInt): PosInt =
        PosInt(value - other.value)

    companion object {
        fun Table.posIntWrapper(name: String): Column<PosInt> =
            this.intWrapper(PosInt::value, ::PosInt)(name)

        val Int.pos: PosInt
            get() = PosInt(this)
    }

    object randomable : Randomable<PosInt> {
        override fun nextRandomInRange(range: ClosedRange<PosInt>): PosInt {
            return (range.start.value..range.endInclusive.value).random().pos
        }
    }

    override fun compareTo(other: PosInt): Int =
        this.value.compareTo(other.value)
}

fun Option<PosInt>.toNonNegOrEmpty() = this.map { it.toNonNeg() }.getOrElse { 0.nonNeg }

@JvmInline
@Serializable
value class NonNegInt(val value: Int) : Comparable<NonNegInt> {
    init {
        require(value >= 0)
    }

    companion object {
        fun Table.nonNegDbWrapper(name: String): Column<NonNegInt> =
            this.intWrapper(NonNegInt::value, ::NonNegInt)(name)

        val Int.nonNeg: NonNegInt
            get() = NonNegInt(this)

        fun Column<NonNegInt>.plus(value: Int): Expression<NonNegInt> =
            PlusOp(this, QueryParameter(value.nonNeg, columnType), columnType)

        fun Column<NonNegInt>.minus(value: Int): Expression<NonNegInt> =
            MinusOp(this, QueryParameter(value.nonNeg, columnType), columnType)

        private val columnType = BaseDBWrapper(IntegerColumnType(), NonNegInt::value, ::NonNegInt)

        class LiteralOpOwn<T>(override val columnType: IColumnType, val value: T) : ExpressionWithColumnType<T>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) =
                queryBuilder {
                    +"'"
                    +columnType.valueToDB(value).toString()
                    +"'"
                }
        }

        fun NonNegInt.literal(): ExpressionWithColumnType<NonNegInt> = LiteralOpOwn<NonNegInt>(columnType, this)
    }

    override fun compareTo(other: NonNegInt): Int =
        value.compareTo(other.value)
}
