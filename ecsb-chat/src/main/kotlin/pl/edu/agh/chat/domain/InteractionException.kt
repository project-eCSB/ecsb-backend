package pl.edu.agh.chat.domain

import io.ktor.http.*
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.DomainException

sealed class InteractionException(userMessage: String, internalMessage: String) : DomainException(
    HttpStatusCode.BadRequest,
    internalMessage,
    userMessage
) {
    class PlayerNotFound(gameSessionId: GameSessionId, playerId: PlayerId) :
        InteractionException(
            "Dude, you are not in the game",
            "Could not find player in game session $gameSessionId for user: $playerId"
        )

    class CannotSetPlayerBusy(gameSessionId: GameSessionId, playerId: PlayerId, busyStatus: InteractionStatus) :
        InteractionException(
            "Unable to give busy status",
            "Unable to give busy status to $playerId in $gameSessionId ($busyStatus)"
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

        class WrongTraveler(
            negotiatedTraveler: PlayerId,
            sentTraveler: PlayerId,
            gameSessionId: GameSessionId,
            travelName: TravelName
        ) :
            TravelException(
                "You tried to travel to $travelName, but it should have been $negotiatedTraveler",
                "$sentTraveler tried to travel to $travelName, but it should have benn $negotiatedTraveler in game $gameSessionId"
            )
    }
}
