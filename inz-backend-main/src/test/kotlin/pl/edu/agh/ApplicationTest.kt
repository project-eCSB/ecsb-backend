package pl.edu.agh

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlin.test.*
import io.ktor.http.*
import org.junit.jupiter.api.Test
import pl.edu.agh.plugins.*
import pl.edu.agh.simple.SimpleHttpRouting

class ApplicationTest {
    @Test
    fun testRoot() = assertEquals(1,1)
}
