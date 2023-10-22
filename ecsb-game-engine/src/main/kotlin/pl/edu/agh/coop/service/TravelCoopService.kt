package pl.edu.agh.coop.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.Money
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.game.dao.ChangeValue
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

interface TravelCoopService {
    suspend fun getTravelCostsByName(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Option<NonEmptyMap<GameResourceName, NonNegInt>> =
        Transactor.dbQuery { TravelDao.getTravelCostsByName(gameSessionId, travelName) }

    suspend fun getTravelByName(gameSessionId: GameSessionId, travelName: TravelName): Option<TravelName> =
        Transactor.dbQuery { TravelDao.getTravelName(gameSessionId, travelName) }

    suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit>

    suspend fun conductCoopPlayerTravel(
        gameSessionId: GameSessionId,
        travelerId: PlayerId,
        secondId: PlayerId,
        resourcesDecideValues: ResourcesDecideValues,
        travelName: TravelName
    ): Either<InteractionException, Unit>
}

class TravelCoopServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val playerResourceService: PlayerResourceService,
) : TravelCoopService {

    override suspend fun conductCoopPlayerTravel(
        gameSessionId: GameSessionId,
        travelerId: PlayerId,
        secondId: PlayerId,
        resourcesDecideValues: ResourcesDecideValues,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            either {
                val (_, timeNeeded, moneyRange, cityCosts) = TravelDao.getTravelData(
                    gameSessionId,
                    travelName
                ).toEither {
                    InteractionException.TravelException.CityNotFound(
                        gameSessionId,
                        travelName
                    )
                }.bind()

                val reward = PosInt((moneyRange.from.value..moneyRange.to.value).random())

                val (_, travelRatio, travelerCosts) = resourcesDecideValues

                val travelerReward = PosInt(travelRatio.value.times(reward.value).toInt())

                val secondReward = reward.minus(travelerReward)

                val secondCosts = cityCosts.diff(travelerCosts).toEither {
                    InteractionException.TravelException.CityNotFound(
                        gameSessionId,
                        travelName
                    )
                }.bind()

                playerResourceService.conductEquipmentChangeOnPlayer(
                    gameSessionId,
                    travelerId,
                    PlayerEquipmentChanges(
                        money = ChangeValue(Money(travelerReward.value.toLong()), Money(0)),
                        resources = travelerCosts.map.mapValues { (_, value) -> ChangeValue(0.nonNeg, value) }
                            .toNonEmptyMapUnsafe(),
                        time = ChangeValue(0.nonNeg, timeNeeded.getOrNull()?.toNonNeg() ?: 0.nonNeg)
                    )
                ) { action ->
                    parZip({
                        interactionProducer.sendMessage(
                            gameSessionId,
                            travelerId,
                            ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart(travelerId)
                        )

                    }, {
                        removeInTravel(gameSessionId, travelerId)
                    }, {
                        action()
                    }, { _, _, _ -> })
                }.mapLeft {
                    InteractionException.TravelException.InsufficientResources(
                        travelerId,
                        gameSessionId,
                        travelName
                    )
                }.bind()

                playerResourceService.conductEquipmentChangeOnPlayer(
                    gameSessionId,
                    secondId,
                    PlayerEquipmentChanges(
                        money = ChangeValue(Money(secondReward.value.toLong()), Money(0)),
                        resources = secondCosts.map.mapValues { (_, value) -> ChangeValue(0.nonNeg, value) }
                            .toNonEmptyMapUnsafe(),
                        time = ChangeValue(0.nonNeg, 0.nonNeg)
                    )
                ) { action ->
                    parZip({
                        interactionProducer.sendMessage(
                            gameSessionId,
                            travelerId,
                            CoopMessages.CoopSystemOutputMessage.TravelCompleted(travelerId, travelName)
                        )

                    }, {
                        removeInTravel(gameSessionId, travelerId)
                    }, {
                        action()
                    }, { _, _, _ -> })
                }.mapLeft {
                    InteractionException.TravelException.InsufficientResources(
                        travelerId,
                        gameSessionId,
                        travelName
                    )
                }.bind()
            }
        }

    override suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            either {
                val (_, timeNeeded, moneyRange, cityCosts) = TravelDao.getTravelData(
                    gameSessionId,
                    travelName
                ).toEither {
                    InteractionException.TravelException.CityNotFound(
                        gameSessionId,
                        travelName
                    )
                }.bind()

                val reward = PosInt((moneyRange.from.value..moneyRange.to.value).random())

                playerResourceService.conductEquipmentChangeOnPlayer(
                    gameSessionId,
                    playerId,
                    PlayerEquipmentChanges(
                        money = ChangeValue(Money(reward.value.toLong()), Money(0)),
                        resources = cityCosts.map.mapValues { (_, value) -> ChangeValue(0.nonNeg, value) }
                            .toNonEmptyMapUnsafe(),
                        time = ChangeValue(0.nonNeg, timeNeeded.getOrNull()?.toNonNeg() ?: 0.nonNeg)
                    )
                ) { action ->
                    parZip({
                        interactionProducer.sendMessage(
                            gameSessionId,
                            playerId,
                            ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart(playerId)
                        )
                    }, {
                        removeInTravel(gameSessionId, playerId)
                    }, {
                        action()
                    }, { _, _, _ -> })
                }.mapLeft {
                    InteractionException.TravelException.InsufficientResources(
                        playerId,
                        gameSessionId,
                        travelName
                    )
                }.bind()
            }
        }

    private suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemOutputMessage.TravelNotification.TravelChoosingStop(playerId)
        )
    }
}
