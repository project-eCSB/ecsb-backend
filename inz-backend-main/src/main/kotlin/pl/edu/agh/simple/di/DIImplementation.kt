package pl.edu.agh.simple.di

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class DIImplementation(val printFunc: (String) -> Unit) : DIInterface {
    override fun <T> printJson(t: T, serializer: KSerializer<T>) {
        printFunc(Json.encodeToString(serializer, t))
    }
}