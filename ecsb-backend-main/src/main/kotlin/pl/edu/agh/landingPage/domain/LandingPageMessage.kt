package pl.edu.agh.landingPage.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.edu.agh.coop.domain.AmountDiff
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
}