package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable

@JvmInline
@Serializable
value class SavedAssetsId(val value: Int)

fun main() {
    val a = SavedAssetsId.serializer().nullable
}