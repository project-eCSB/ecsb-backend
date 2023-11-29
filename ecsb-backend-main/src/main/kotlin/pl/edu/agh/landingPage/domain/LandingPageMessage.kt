package pl.edu.agh.landingPage.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.AmountDiff
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.NonNegInt

@Serializable
sealed interface LandingPageMessage {
    @Serializable
    @SerialName("landing_page/change")
    data class LandingPageMessageMain(val playersAmount: AmountDiff<NonNegInt>, val players: List<PlayerId>) :
        LandingPageMessage

    @Serializable
    @SerialName("landing_page/game_started")
    object GameStarted : LandingPageMessage

    @Serializable
    @SerialName("landing_page/game_ended")
    object GameEnded : LandingPageMessage
}
