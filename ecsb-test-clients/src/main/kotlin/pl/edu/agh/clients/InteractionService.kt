package pl.edu.agh.clients

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import pl.edu.agh.equipment.domain.PlayerEquipment

class InteractionService(val client: HttpClient, val mainUrl: String, val chatUrl: String) {

    suspend fun produce(gameToken: String, quantity: Int) {
        val before = client.get("$mainUrl/equipment") {
            bearerAuth(gameToken)
            contentType(ContentType.Application.Json)
        }.body<PlayerEquipment>()

        val (code, description) = client.post("$chatUrl/production") {
            bearerAuth(gameToken)
            contentType(ContentType.Application.Json)
            setBody(quantity)
        }.body<HttpStatusCode>()

        val after = client.get("$mainUrl/equipment") {
            bearerAuth(gameToken)
            contentType(ContentType.Application.Json)
        }.body<PlayerEquipment>()

        println("Before: $before")
        println("After: $after")
    }
}
