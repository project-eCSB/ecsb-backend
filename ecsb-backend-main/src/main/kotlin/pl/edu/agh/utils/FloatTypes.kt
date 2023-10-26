package pl.edu.agh.utils

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

@JvmInline
@Serializable
value class PosFloat(val value: Float) {
    init {
        require(value > 0)
    }

    fun toNonNeg(): NonNegFloat = NonNegFloat(value)

    operator fun times(other: PosFloat): PosFloat =
        PosFloat(other.value * value)

    companion object {
        fun Table.posFloatWrapper(name: String): Column<PosFloat> =
            this.floatWrapper(PosFloat::value, ::PosFloat)(name)

        val Float.pos: PosFloat
            get() = PosFloat(this)
    }
}

@JvmInline
@Serializable
value class NonNegFloat(val value: Float) : Comparable<NonNegFloat> {
    init {
        require(value >= 0)
    }

    companion object {
        fun Table.nonNegDbWrapper(name: String): Column<NonNegFloat> =
            this.floatWrapper(NonNegFloat::value, ::NonNegFloat)(name)

        val Float.nonNeg: NonNegFloat
            get() = NonNegFloat(this)

        fun Column<NonNegFloat>.plus(value: Float): Expression<NonNegFloat> =
            PlusOp(this, QueryParameter(value.nonNeg, columnType), columnType)

        fun Column<NonNegFloat>.minus(value: Float): Expression<NonNegFloat> =
            MinusOp(this, QueryParameter(value.nonNeg, columnType), columnType)

        private val columnType = BaseDBWrapper(FloatColumnType(), NonNegFloat::value, ::NonNegFloat)

        class LiteralOpOwn<T>(override val columnType: IColumnType, val value: T) : ExpressionWithColumnType<T>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) =
                queryBuilder {
                    +"'"
                    +columnType.valueToDB(value).toString()
                    +"'"
                }
        }

        fun NonNegFloat.literal(): ExpressionWithColumnType<NonNegFloat> = LiteralOpOwn<NonNegFloat>(columnType, this)
    }

    override fun compareTo(other: NonNegFloat): Int =
        value.compareTo(other.value)

    fun plus(other: NonNegFloat): NonNegFloat =
        NonNegFloat(value + other.value)
}
