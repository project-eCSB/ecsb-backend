package pl.edu.agh.simple.di

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

interface DIInterface {
    fun <T> printJson(t: T, serializer: KSerializer<T>): Unit
}