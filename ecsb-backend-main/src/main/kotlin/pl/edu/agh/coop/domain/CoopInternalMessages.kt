package pl.edu.agh.coop.domain

import arrow.core.Either
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.PosInt

typealias ResourcesDecideValues = OptionS<Pair<PlayerId, NonEmptyMap<GameResourceName, PosInt>>>

interface WaitingCoopEnd
typealias CityDecideVotes = OptionS<NonEmptyMap<TravelName, PosInt>>
typealias ErrorOr<T> = Either<String, T>

@Serializable
sealed interface CoopInternalMessages {
    @Serializable
    object CancelCoopAtAnyStage : CoopInternalMessages

    @Serializable
    data class FindCoop(val cityName: TravelName) : CoopInternalMessages

    @Serializable
    data class FindCoopAck(val cityName: TravelName, val proposalSenderId: PlayerId) : CoopInternalMessages

    @Serializable
    data class ProposeCoop(val receiverId: PlayerId) : CoopInternalMessages

    @Serializable
    data class ProposeCoopAck(val senderId: PlayerId) : CoopInternalMessages

    @Serializable
    data class CityVotes(val currentVotes: CityDecideVotes) : CoopInternalMessages

    @Serializable
    data class CityVoteAck(val travelName: TravelName) : CoopInternalMessages

    @Serializable
    sealed interface SystemInputMessage : CoopInternalMessages {
        @Serializable
        data class FindCoopAck(val cityName: TravelName, val senderId: PlayerId) : SystemInputMessage

        @Serializable
        data class CityVoteAck(val travelName: TravelName) : SystemInputMessage

        @Serializable
        object CityVotes : SystemInputMessage

        @Serializable
        object ResourcesGathered : SystemInputMessage

        @Serializable
        object TravelDone : SystemInputMessage

        @Serializable
        data class ResourcesDecideAck(val otherPlayerResources: ResourcesDecideValues) : SystemInputMessage

        @Serializable
        data class ResourcesDecide(val yourResourcesDecide: ResourcesDecideValues) : SystemInputMessage

        @Serializable
        object EndOfTravelReady : SystemInputMessage
    }

    @Serializable
    data class ResourcesDecide(val resourcesDecideValues: ResourcesDecideValues) : CoopInternalMessages

    @Serializable
    data class ResourcesDecideAck(val resourcesDecideValues: ResourcesDecideValues): CoopInternalMessages
}
