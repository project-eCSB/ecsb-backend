package pl.edu.agh.coop.domain

import arrow.core.none
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

class CoopPlayerEquipmentTest {
    @Test
    fun `error when unsufficient resource`() {
        val equipment =
            CoopPlayerEquipment(NonEmptyMap.fromListUnsafe(listOf(GameResourceName("gówno") to AmountDiff(0.nonNeg to 3.nonNeg))), none())
        Assertions.assertTrue(equipment.validate().isLeft())
    }

    @Test
    fun `go when sufficient resource`() {
        val equipment =
            CoopPlayerEquipment(NonEmptyMap.fromListUnsafe(listOf(GameResourceName("gówno") to AmountDiff(3.nonNeg to 3.nonNeg))), none())
        Assertions.assertTrue(equipment.validate().isRight())
    }
}
