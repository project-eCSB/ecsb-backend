package pl.edu.agh

import io.ktor.server.application.*
import io.ktor.server.auth.*

object PrometheusRoute {

    fun Application.configurePrometheusRoute() {
        install(Authentication) {
            configurePrometheusRoute()
        }
    }

    fun AuthenticationConfig.configurePrometheusRoute() {
        basic("metrics") {
            validate {
                if (it.name == "metrics" && it.password == "123123lkjlkjoia4weu#@$%fsdvfadfkhuao") {
                    UserIdPrincipal("metrics")
                } else {
                    null
                }
            }
        }
    }
}
