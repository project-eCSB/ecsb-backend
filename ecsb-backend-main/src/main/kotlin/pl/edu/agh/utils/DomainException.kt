package pl.edu.agh.utils

import io.ktor.http.*

object DomainExceptionLogger {
    val logger by LoggerDelegate()
}

open class DomainException(
    private val httpStatusCode: HttpStatusCode,
    private val internalMessage: String,
    private val userMessage: String
) {
    fun toResponsePairLogging(): Pair<HttpStatusCode, String> {
        DomainExceptionLogger.logger.info(internalMessage)
        return httpStatusCode to userMessage
    }
}
