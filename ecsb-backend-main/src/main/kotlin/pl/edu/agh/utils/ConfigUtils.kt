package pl.edu.agh.utils

import arrow.core.Option
import arrow.core.getOrElse
import com.sksamuel.hoplite.ConfigLoaderBuilder

object ConfigUtils {
    inline fun <reified T : Any> getConfigOrThrow(): T =
        ConfigLoaderBuilder.default().build().let { configLoader ->
            Option.fromNullable(System.getProperty("config")).map {
                configLoader.loadConfigOrThrow<T>(it)
            }.getOrElse {
                configLoader.loadConfigOrThrow<T>("/application.conf")
            }
        }
}
