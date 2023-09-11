package pl.edu.agh.utils

enum class ExchangeType(val value: String) {
    SHARDING("x-modulus-hash"), FANOUT("fanout"), TOPIC("topic")
}
