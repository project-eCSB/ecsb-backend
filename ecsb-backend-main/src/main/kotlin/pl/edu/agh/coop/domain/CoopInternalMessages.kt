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
    object CancelCoopAtAnyStage : CoopInternalMessages

    class FindCoop(val cityName: TravelName) : CoopInternalMessages
    class FindCoopAck(val cityName: TravelName, val senderId: PlayerId) : CoopInternalMessages

    class ProposeCoop(val receiverId: PlayerId) : CoopInternalMessages
    class ProposeCoopAck(val senderId: PlayerId) : CoopInternalMessages

    class CityVotes(val currentVotes: CityDecideVotes) : CoopInternalMessages
    class CityVoteAck(val travelName: TravelName) : CoopInternalMessages

    class WillTakeCareOfMessage(val willTakeCareOf: WillTakeCareOf) : CoopInternalMessages

    object StartResourcesDecide : CoopInternalMessages

    sealed class SystemInputMessage : CoopInternalMessages {
        class FindCoopAck(val cityName: TravelName, val senderId: PlayerId) : SystemInputMessage()

        class CityVoteAck(val travelName: TravelName) : CoopInternalMessages
        object CityVotes : SystemInputMessage()

        object ResourcesGathered : SystemInputMessage()
        object StartResourcesPassiveDecide : SystemInputMessage()

        object TravelDone : SystemInputMessage()

        object ResourcesDecideAck : SystemInputMessage()
        class ResourcesDecideReject(val yourResourcesDecide: ResourcesDecideValues) : SystemInputMessage()

        object EndOfTravelReady : SystemInputMessage()
    }

    class ResourcesDecide(val resourcesDecideValues: ResourcesDecideValues) : CoopInternalMessages
    object ResourcesDecideAck : CoopInternalMessages
}
