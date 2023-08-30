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
class CoopService(private val interactionProducer: InteractionProducer<CoopInternalMessages>) {

    private val logger by LoggerDelegate()

    suspend fun handleIncomingCoopMessage(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        coopMessage: CoopMessages.CoopUserInputMessage
    ) {
        val sentAt = LocalDateTime.now()
        logger.info("Player $playerId sent message in game $gameSessionId with content $coopMessage at $sentAt")
        val sender = interactionProducer::sendMessage.partially1(gameSessionId).partially1(playerId)
        when (coopMessage) {
            is CoopMessages.CoopUserInputMessage.FindCoop -> sender(CoopInternalMessages.FindCoop(coopMessage.travelName))
            is CoopMessages.CoopUserInputMessage.FindCoopAck -> sender(
                CoopInternalMessages.FindCoopAck(
                    coopMessage.travelName,
                    coopMessage.playerId
                )
            )

            is CoopMessages.CoopUserInputMessage.ResourceDecideAck -> sender(
                CoopInternalMessages.ResourcesDecideAck(
                    coopMessage.resources
                )
            )

            is CoopMessages.CoopUserInputMessage.ResourceDecideChange -> sender(
                CoopInternalMessages.ResourcesDecide(
                    coopMessage.resources
                )
            )

            is CoopMessages.CoopUserInputMessage.CityDecide -> sender(
                CoopInternalMessages.CityVotes(coopMessage.playerVotes)
            )

            is CoopMessages.CoopUserInputMessage.CityDecideAck -> sender(
                CoopInternalMessages.CityVoteAck(coopMessage.travelName)
            )

            is CoopMessages.CoopUserInputMessage.ProposeCoop -> sender(
                CoopInternalMessages.ProposeCoop(coopMessage.playerId)
            )

            is CoopMessages.CoopUserInputMessage.ProposeCoopAck -> sender(
                CoopInternalMessages.ProposeCoopAck(coopMessage.playerId)
            )

            CoopMessages.CoopUserInputMessage.RenegotiateCityRequest -> sender(CoopInternalMessages.RenegotiateCityRequest)
            CoopMessages.CoopUserInputMessage.RenegotiateResourcesRequest -> sender(CoopInternalMessages.RenegotiateResourcesRequest)

            CoopMessages.CoopUserInputMessage.CancelCoopAtAnyStage -> sender(CoopInternalMessages.CancelCoopAtAnyStage)
        }
    }
}
