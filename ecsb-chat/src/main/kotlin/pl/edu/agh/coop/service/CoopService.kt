package pl.edu.agh.coop.service

import arrow.core.partially1
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime

/**
 * Resends received external CoopMessages as CoopInternalMessages to game engine module & analytics module
 */
class CoopService(private val coopInternalMessageProducer: InteractionProducer<CoopInternalMessages.UserInputMessage>) {

    private val logger by LoggerDelegate()

    suspend fun handleIncomingCoopMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        coopMessage: CoopMessages.CoopUserInputMessage
    ) {
        val sentAt = LocalDateTime.now()
        logger.info("Player $playerId sent message in game $gameSessionId with content $coopMessage at $sentAt")
        val sender = coopInternalMessageProducer::sendMessage.partially1(gameSessionId).partially1(playerId)
        when (coopMessage) {
            CoopMessages.CoopUserInputMessage.CancelCoopAtAnyStage -> sender(CoopInternalMessages.UserInputMessage.CancelCoopAtAnyStage)
            CoopMessages.CoopUserInputMessage.CancelNegotiationAtAnyStage -> sender(CoopInternalMessages.UserInputMessage.CancelNegotiationAtAnyStage)
            CoopMessages.CoopUserInputMessage.CancelPlanningAtAnyStage -> sender(CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage)
            is CoopMessages.CoopUserInputMessage.AdvertisePlanning -> sender(CoopInternalMessages.UserInputMessage.StartAdvertisingCoop)
            is CoopMessages.CoopUserInputMessage.SimpleJoinPlanning -> sender(
                CoopInternalMessages.UserInputMessage.SimpleJoinPlanningUser(
                    playerId,
                    coopMessage.ownerId
                )
            )

            is CoopMessages.CoopUserInputMessage.SimpleJoinPlanningAck -> sender(
                CoopInternalMessages.UserInputMessage.SimpleJoinPlanningAckUser(
                    playerId,
                    coopMessage.guestId
                )
            )

            is CoopMessages.CoopUserInputMessage.GatheringJoinPlanning -> sender(
                CoopInternalMessages.UserInputMessage.GatheringJoinPlanningUser(
                    playerId,
                    coopMessage.ownerId
                )
            )

            is CoopMessages.CoopUserInputMessage.GatheringJoinPlanningAck -> sender(
                CoopInternalMessages.UserInputMessage.GatheringJoinPlanningAckUser(
                    playerId,
                    coopMessage.otherOwnerId
                )
            )

            is CoopMessages.CoopUserInputMessage.ProposeOwnTravel -> sender(
                CoopInternalMessages.UserInputMessage.ProposeOwnTravelUser(
                    playerId,
                    coopMessage.guestId,
                    coopMessage.travelName
                )
            )

            is CoopMessages.CoopUserInputMessage.ProposeOwnTravelAck -> sender(
                CoopInternalMessages.UserInputMessage.ProposeOwnTravelAckUser(
                    playerId,
                    coopMessage.ownerId,
                    coopMessage.travelName
                )
            )

            is CoopMessages.CoopUserInputMessage.ResourceDecide -> sender(
                CoopInternalMessages.UserInputMessage.ResourcesDecideUser(
                    coopMessage.yourBid
                )
            )

            is CoopMessages.CoopUserInputMessage.ResourceDecideAck -> sender(
                CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser(
                    coopMessage.yourBid
                )
            )

            is CoopMessages.CoopUserInputMessage.StartPlanning -> sender(
                CoopInternalMessages.UserInputMessage.StartPlanning(
                    playerId,
                    coopMessage.travelName
                )
            )

            CoopMessages.CoopUserInputMessage.StopAdvertisingPlanning -> sender(CoopInternalMessages.UserInputMessage.StopAdvertisingCoop)
            is CoopMessages.CoopUserInputMessage.StartPlannedTravel -> sender(
                CoopInternalMessages.UserInputMessage.StartPlannedTravel(
                    playerId,
                    coopMessage.travelName
                )
            )

            is CoopMessages.CoopUserInputMessage.StartSimpleTravel -> sender(
                CoopInternalMessages.UserInputMessage.StartSimpleTravel(
                    playerId,
                    coopMessage.travelName
                )
            )
        }
    }

    suspend fun syncAdvertisement(gameSessionId: GameSessionId, playerId: PlayerId) {
        coopInternalMessageProducer.sendMessage(
            gameSessionId,
            playerId,
            CoopInternalMessages.UserInputMessage.SyncAdvertisement
        )
    }

    suspend fun cancelCoopNegotiationAndAdvertisement(gameSessionId: GameSessionId, playerId: PlayerId) =
        coopInternalMessageProducer.sendMessage(
            gameSessionId,
            playerId,
            CoopInternalMessages.UserInputMessage.ExitGameSession
        )
}
