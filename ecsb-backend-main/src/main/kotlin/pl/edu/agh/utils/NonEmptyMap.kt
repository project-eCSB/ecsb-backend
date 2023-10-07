package pl.edu.agh.utils

import arrow.core.Option
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = NonEmptyMapSerializer::class)
data class NonEmptyMap<K, V>(val map: Map<K, V>) : Map<K, V> {

    init {
        require(map.isNotEmpty())
    }

    companion object {
        fun <K, V> fromMapSafe(map: Map<K, V>): Option<NonEmptyMap<K, V>> = Option.catch { NonEmptyMap(map) }

        fun <K, V> fromListSafe(list: Iterable<Pair<K, V>>): Option<NonEmptyMap<K, V>> =
            Option.catch { NonEmptyMap(list.toMap()) }

        fun <K, V> fromMapUnsafe(map: Map<K, V>): NonEmptyMap<K, V> = NonEmptyMap(map)

        fun <K, V> fromListUnsafe(list: Iterable<Pair<K, V>>): NonEmptyMap<K, V> = NonEmptyMap(list.toMap())
    }

    override val entries: Set<Map.Entry<K, V>> = map.entries
    override val keys: Set<K> = map.keys
    override val size: Int = map.size
    override val values: Collection<V> = map.values

    override fun containsKey(key: K): Boolean = map.containsKey(key)

    override fun containsValue(value: V): Boolean = map.containsValue(value)

    override fun get(key: K): V? = map[key]

    override fun isEmpty(): Boolean = false
}

fun <K, V> nonEmptyMapOf(first: Pair<K, V>, vararg pairs: Pair<K, V>): NonEmptyMap<K, V> =
    NonEmptyMap(mapOf(first, *pairs))

fun <K, V> List<Pair<K, V>>.toNonEmptyMapOrNone(): Option<NonEmptyMap<K, V>> =
    NonEmptyMap.fromListSafe(this)

fun <K, V> List<Pair<K, V>>.toNonEmptyMapUnsafe(): NonEmptyMap<K, V> =
    NonEmptyMap.fromListUnsafe(this)

fun <K, V> Map<K, V>.toNonEmptyMapUnsafe(): NonEmptyMap<K, V> =
    NonEmptyMap.fromMapUnsafe(this)

fun <K, V> Map<K, V>.toNonEmptyMapOrNone(): Option<NonEmptyMap<K, V>> =
    NonEmptyMap.fromMapSafe(this)

@Serializable
data class MapEntry<K, V>(val key: K, val value: V) {
    companion object {
        fun <K, V> fromPair(pair: Pair<K, V>): MapEntry<K, V> = MapEntry(pair.first, pair.second)
    }
}

class NonEmptyMapSerializer<K, V>(keySerializer: KSerializer<K>, valueSerializer: KSerializer<V>) :
    KSerializer<NonEmptyMap<K, V>> {

    private val serializer: KSerializer<List<MapEntry<K, V>>> =
        ListSerializer(MapEntry.serializer(keySerializer, valueSerializer))

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): NonEmptyMap<K, V> =
        serializer.deserialize(decoder).associate { it.key to it.value }.let {
            NonEmptyMap(it)
        }

    override fun serialize(encoder: Encoder, value: NonEmptyMap<K, V>) {
        value.map.toList().map { MapEntry.fromPair(it) }.let {
            serializer.serialize(encoder, it)
        }
    }
}
