package pl.edu.agh.chat.domain

import io.ktor.http.*
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.DomainException

sealed class InteractionException(userMessage: String, internalMessage: String) : DomainException(
    HttpStatusCode.BadRequest,
    userMessage,
    internalMessage
) {
    class PlayerNotFound(gameSessionId: GameSessionId, playerId: PlayerId) :
        InteractionException(
            "Dude, you are not in the game",
            "Could not find player in game session $gameSessionId for user: $playerId"
        )

    class ResourcesException(gameSessionId: GameSessionId, playerId: PlayerId) :
        InteractionException(
            "Error on getting your resources in game $gameSessionId",
            "Error on getting $playerId resources in game $gameSessionId"
        )

    sealed class ProductionException(userMessage: String, internalMessage: String) :
        InteractionException(userMessage, internalMessage) {

        class InsufficientResource(
            playerId: PlayerId,
            gameResourceName: GameResourceName,
            quantity: Int
        ) :
            ProductionException(
                "You're too poor, your equipment is not enough to produce $quantity $gameResourceName",
                "Player $playerId has too little of everything to produce $quantity $gameResourceName"
            )

        class NegativeResource(playerId: PlayerId, gameResourceName: GameResourceName, quantity: Int) :
            ProductionException(
                "You're trying to produce negative ($quantity) of $gameResourceName",
                "Player $playerId wants to produce negative ($quantity) of $gameResourceName"
            )
    }

    sealed class TravelException(userMessage: String, internalMessage: String) : InteractionException(
        userMessage,
        internalMessage
    ) {
        class InsufficientResources(
            playerId: PlayerId,
            gameSessionId: GameSessionId,
            travelName: TravelName
        ) :
            TravelException(
                "You got insufficient resources for travel to $travelName",
                "Player $playerId in game $gameSessionId wanted to travel to $travelName, but has insufficient resources"
            )

        class CityNotFound(
            gameSessionId: GameSessionId,
            travelName: TravelName
        ) :
            TravelException(
                "There is no such city $travelName to travel mate",
                "There is no $travelName city in game $gameSessionId"
            )
    }
}
