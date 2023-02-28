package pl.edu.agh

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlin.test.*
import io.ktor.http.*
import pl.edu.agh.plugins.*
import pl.edu.agh.simple.SimpleHttpRouting

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
            SimpleHttpRouting()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("hello", bodyAsText())
        }
        client.get("/1").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("hello 1", bodyAsText())
        }
    }
}
