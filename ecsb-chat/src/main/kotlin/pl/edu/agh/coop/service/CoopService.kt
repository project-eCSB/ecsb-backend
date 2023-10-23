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
            CoopMessages.CoopUserInputMessage.CancelPlanningAtAnyStage -> sender(CoopInternalMessages.UserInputMessage.CancelPlanningAtAnyStage)
            is CoopMessages.CoopUserInputMessage.FindCompanyForPlanning -> sender(CoopInternalMessages.UserInputMessage.FindCompanyForPlanning)
            is CoopMessages.CoopUserInputMessage.JoinPlanning -> sender(
                CoopInternalMessages.UserInputMessage.JoinPlanningUser(
                    playerId,
                    coopMessage.ownerId
                )
            )

            is CoopMessages.CoopUserInputMessage.JoinPlanningAck -> sender(
                CoopInternalMessages.UserInputMessage.JoinPlanningAckUser(
                    playerId,
                    coopMessage.guestId
                )
            )

            is CoopMessages.CoopUserInputMessage.ProposeCompany -> sender(
                CoopInternalMessages.UserInputMessage.ProposeCompanyUser(
                    playerId,
                    coopMessage.guestId,
                    coopMessage.travelName
                )
            )

            is CoopMessages.CoopUserInputMessage.ProposeCompanyAck -> sender(
                CoopInternalMessages.UserInputMessage.ProposeCompanyAckUser(
                    playerId,
                    coopMessage.ownerId,
                    coopMessage.travelName
                )
            )

            is CoopMessages.CoopUserInputMessage.ResourceDecide -> sender(
                CoopInternalMessages.UserInputMessage.ResourcesDecideUser(
                    playerId,
                    coopMessage.yourBid,
                    coopMessage.otherPlayerId
                )
            )

            is CoopMessages.CoopUserInputMessage.ResourceDecideAck -> sender(
                CoopInternalMessages.UserInputMessage.ResourcesDecideAckUser(
                    playerId,
                    coopMessage.otherPlayerBid,
                    coopMessage.otherPlayerId
                )
            )

            is CoopMessages.CoopUserInputMessage.StartPlanning -> sender(
                CoopInternalMessages.UserInputMessage.StartPlanning(
                    playerId,
                    coopMessage.cityName
                )
            )

            CoopMessages.CoopUserInputMessage.StopFindingCompany -> sender(CoopInternalMessages.UserInputMessage.StopFindingCompany)
            is CoopMessages.CoopUserInputMessage.StartTravel -> sender(
                CoopInternalMessages.UserInputMessage.StartTravel(
                    playerId,
                    coopMessage.travelName
                )
            )
        }
    }
}
