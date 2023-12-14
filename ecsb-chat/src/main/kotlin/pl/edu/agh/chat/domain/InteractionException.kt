package pl.edu.agh.chat.domain

import io.ktor.http.*
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.DomainException

sealed class InteractionException(userMessage: String, internalMessage: String) : DomainException(
    HttpStatusCode.BadRequest,
    internalMessage,
    userMessage
) {
    class PlayerNotFound(gameSessionId: GameSessionId, playerId: PlayerId) :
        InteractionException(
            "Niestety nie jesteś w grze",
            "Could not find player in game session $gameSessionId for user: $playerId"
        )

    class CannotSetPlayerBusy(gameSessionId: GameSessionId, playerId: PlayerId, busyStatus: InteractionStatus) :
        InteractionException(
            "Nie można było ustawić ci statusu jako zajety",
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
                "Jesteś za biedy, twój ekwipunek nie stać na wyprodukowanie $quantity $gameResourceName",
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
                "Masz za mało zasobów by podróżować do $travelName",
                "Player $playerId in game $gameSessionId wanted to travel to $travelName, but has insufficient resources"
            )

        class CityNotFound(
            gameSessionId: GameSessionId,
            travelName: TravelName
        ) :
            TravelException(
                "Nie ma takiego miasta jak $travelName",
                "There is no $travelName city in game $gameSessionId"
            )

        class WrongTraveler(
            negotiatedTraveler: PlayerId,
            sentTraveler: PlayerId,
            gameSessionId: GameSessionId,
            travelName: TravelName
        ) :
            TravelException(
                "Próbowałeś podróżować do  $travelName, ale to powinien być $negotiatedTraveler",
                "$sentTraveler tried to travel to $travelName, but it should have benn $negotiatedTraveler in game $gameSessionId"
            )
    }
}
