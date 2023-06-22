package pl.edu.agh.coop.domain

import arrow.core.Either
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonEmptySetS
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.PosInt

typealias ResourcesDecideValues = OptionS<Pair<PlayerId, NonEmptyMap<GameResourceName, PosInt>>>

interface WaitingCoopEnd
typealias CityDecideVotes = OptionS<NonEmptyMap<TravelName, PosInt>>
typealias WillTakeCareOf = OptionS<NonEmptySetS<GameResourceName>>
typealias ErrorOr<T> = Either<String, T>

@Serializable
sealed interface CoopInternalMessages {
    @Serializable
    object CancelCoopAtAnyStage : CoopInternalMessages

    @Serializable
    class FindCoop(val cityName: TravelName) : CoopInternalMessages

    @Serializable
    class FindCoopAck(val cityName: TravelName, val senderId: PlayerId) : CoopInternalMessages

    @Serializable
    class ProposeCoop(val receiverId: PlayerId) : CoopInternalMessages

    @Serializable
    class ProposeCoopAck(val senderId: PlayerId) : CoopInternalMessages

    @Serializable
    class CityVotes(val currentVotes: CityDecideVotes) : CoopInternalMessages

    @Serializable
    class CityVoteAck(val travelName: TravelName) : CoopInternalMessages

    @Serializable
    class WillTakeCareOfMessage(val willTakeCareOf: WillTakeCareOf) : CoopInternalMessages

    @Serializable
    object StartResourcesDecide : CoopInternalMessages

    @Serializable
    sealed interface SystemInputMessage : CoopInternalMessages {
        @Serializable
        class FindCoopAck(val cityName: TravelName, val senderId: PlayerId) : SystemInputMessage

        @Serializable
        class CityVoteAck(val travelName: TravelName) : SystemInputMessage

        @Serializable
        object CityVotes : SystemInputMessage

        @Serializable
        object ResourcesGathered : SystemInputMessage

        @Serializable
        object StartResourcesPassiveDecide : SystemInputMessage

        @Serializable
        object TravelDone : SystemInputMessage

        @Serializable
        object ResourcesDecideAck : SystemInputMessage

        @Serializable
        class ResourcesDecideReject(val yourResourcesDecide: ResourcesDecideValues) : SystemInputMessage

        @Serializable
        object EndOfTravelReady : SystemInputMessage
    }

    @Serializable
    class ResourcesDecide(val resourcesDecideValues: ResourcesDecideValues) : CoopInternalMessages

    @Serializable
    object ResourcesDecideAck : CoopInternalMessages
}
