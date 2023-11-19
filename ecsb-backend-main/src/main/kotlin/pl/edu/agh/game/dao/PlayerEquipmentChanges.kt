package pl.edu.agh.game.dao

import arrow.core.Ior
import arrow.core.padZip
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.MinusOp
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.equipment.domain.PlayerEquipment
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.toNonEmptyMapUnsafe

data class PlayerEquipmentChanges(
    val money: ChangeValue<Money> = ChangeValue(Money(0), Money(0)),
    val resources: NonEmptyMap<GameResourceName, ChangeValue<NonNegInt>>,
    val time: ChangeValue<NonNegInt> = ChangeValue(0.nonNeg, 0.nonNeg)
) {
    companion object {
        fun createFromEquipments(additions: PlayerEquipment, deletions: PlayerEquipment): PlayerEquipmentChanges {
            val money = ChangeValue(additions.money, deletions.money)
            val resources = additions.resources.padZip(deletions.resources).map { (key, changes) ->
                val diff = Ior.fromNullables(changes.first, changes.second)
                key to when (diff) {
                    is Ior.Both -> ChangeValue(diff.leftValue, diff.rightValue)
                    is Ior.Left -> ChangeValue(diff.value, NonNegInt(0))
                    is Ior.Right -> ChangeValue(NonNegInt(0), diff.value)
                    null -> error("This should not happen")
                }
            }.toNonEmptyMapUnsafe()
            return PlayerEquipmentChanges(money, resources, ChangeValue.empty(NonNegInt(0)))
        }
    }
}

data class ChangeValue<T>(val addition: T, val deletions: T) {
    companion object {
        fun <T> empty(value: T): ChangeValue<T> =
            ChangeValue(value, value)
    }
}

fun <T : Number> ChangeValue<T>.diff(): Int = addition.toInt() - deletions.toInt()

fun <T, T1> ChangeValue<T>.map(f: (T) -> T1): ChangeValue<T1> =
    ChangeValue(f(addition), f(deletions))

fun <T> ChangeValue<T>.addToColumn(column: Column<T>): MinusOp<T, T> {
    return column + this.addition - this.deletions
}
