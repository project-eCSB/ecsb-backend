package pl.edu.agh.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.junit.JUnitAsserter

class NonEmptyMapSerializerTest {

    private val format = Json

    private fun <T> test(tSerializer: KSerializer<T>, adt: T, strEquivalent: String) {
        JUnitAsserter.assertEquals(
            "encoded adt was not equal to strEquivalent",
            strEquivalent,
            format.encodeToString(tSerializer, adt)
        )

        val adt2 = format.decodeFromString(tSerializer, strEquivalent)

        JUnitAsserter.assertEquals("decoded str was not equal to adt", adt, adt2)
    }

    @Test
    fun `test nonEmptyMapSerializer for valid json case`() {
        val adt: NonEmptyMap<Int, Int> = nonEmptyMapOf(1 to 2, 3 to 4)
        val json = """[{"key":1,"value":2},{"key":3,"value":4}]"""
        test(NonEmptyMap.serializer(Int.serializer(), Int.serializer()), adt, json)
    }
}
