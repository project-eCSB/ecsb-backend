package pl.edu.agh.coop.service

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.coop.domain.ResourcesDecideValues
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.equipmentChangeQueue.dao.EquipmentChangeQueueDao
import pl.edu.agh.equipmentChangeQueue.domain.PlayerEquipmentAdditions
import pl.edu.agh.game.dao.ChangeValue
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import kotlin.time.Duration.Companion.seconds

interface TravelCoopService {
    suspend fun getTravelCostsByName(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Option<NonEmptyMap<GameResourceName, NonNegInt>> =
        Transactor.dbQuery { TravelDao.getTravelCostsByName(gameSessionId, travelName) }

    suspend fun getTravelByName(gameSessionId: GameSessionId, travelName: TravelName): Option<TravelName> =
        Transactor.dbQuery { TravelDao.getTravelName(gameSessionId, travelName) }

    suspend fun conductCoopPlayerTravel(
        gameSessionId: GameSessionId,
        travelerId: PlayerId,
        secondId: PlayerId,
        resourcesDecideValues: ResourcesDecideValues,
        travelName: TravelName
    ): Either<InteractionException, Unit>

    suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit>
}

class TravelCoopServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val playerResourceService: PlayerResourceService,
) : TravelCoopService {
    private val timeout = 5.seconds

    private val logger by LoggerDelegate()

    private fun costsToChangeValues(costs: NonEmptyMap<GameResourceName, NonNegInt>) =
        costs.map.mapValues { (_, value) -> ChangeValue(0.nonNeg, value) }.toNonEmptyMapUnsafe()

    private fun getSecondPlayerResourcesChangeValues(
        gameSessionId: GameSessionId,
        travelName: TravelName,
        cityCosts: NonEmptyMap<GameResourceName, NonNegInt>,
        firstPlayerCosts: NonEmptyMap<GameResourceName, NonNegInt>
    ): Either<InteractionException, NonEmptyMap<GameResourceName, ChangeValue<NonNegInt>>> =
        cityCosts.diff(firstPlayerCosts).map(::costsToChangeValues).toEither {
            InteractionException.TravelException.CityNotFound(
                gameSessionId,
                travelName
            )
        }

    private suspend fun getTravelData(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Either<InteractionException, GameTravelsView> = Transactor.dbQuery {
        TravelDao.getTravelData(
            gameSessionId,
            travelName
        ).toEither {
            InteractionException.TravelException.CityNotFound(
                gameSessionId,
                travelName
            )
        }
    }

    override suspend fun conductCoopPlayerTravel(
        gameSessionId: GameSessionId,
        travelerId: PlayerId,
        secondId: PlayerId,
        resourcesDecideValues: ResourcesDecideValues,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        either {
            val (negotiatedTraveler, travelRatio, travelerCosts) = resourcesDecideValues
            ensure(negotiatedTraveler == travelerId) {
                InteractionException.TravelException.WrongTraveler(
                    negotiatedTraveler,
                    travelerId,
                    gameSessionId,
                    travelName,
                )
            }
            val (_, maybeTimeNeeded, moneyRange, cityCosts) = getTravelData(gameSessionId, travelName).bind()

            val timeNeeded = maybeTimeNeeded.toNonNegOrEmpty()
            val reward: PosInt = moneyRange.random(PosInt.randomable)

            val rewards: SplitValue = travelRatio.splitValue(reward)
            val travelerReward: NonNegInt = rewards.inPercentiles
            val secondReward: NonNegInt = rewards.notInPercentiles

            val secondCosts = getSecondPlayerResourcesChangeValues(
                gameSessionId,
                travelName,
                cityCosts = cityCosts,
                firstPlayerCosts = travelerCosts
            ).bind()

            playerResourceService.conductEquipmentChangeOnPlayers(
                gameSessionId,
                nonEmptyMapOf(
                    travelerId to PlayerEquipmentChanges(
                        resources = costsToChangeValues(travelerCosts),
                        time = ChangeValue(0.nonNeg, timeNeeded)
                    ),
                    secondId to
                            PlayerEquipmentChanges(
                                resources = secondCosts
                            )
                ),
                EquipmentInternalMessage::EquipmentChangeAfterCoop
            ) { action ->
                parZip({
                    interactionProducer.sendMessage(
                        gameSessionId,
                        travelerId,
                        ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart(timeout)
                    )
                }, {
                    Transactor.dbQuery {
                        EquipmentChangeQueueDao.addItemToProcessing(
                            TimestampMillis(timeout.inWholeMilliseconds),
                            gameSessionId,
                            travelerId,
                            PlayerEquipmentAdditions.money(
                                Money(travelerReward)
                            ),
                            "travel"
                        )()
                        EquipmentChangeQueueDao.addItemToProcessing(
                            TimestampMillis(timeout.inWholeMilliseconds),
                            gameSessionId,
                            secondId,
                            PlayerEquipmentAdditions.money(
                                Money(secondReward)
                            ),
                            "travel"
                        )()
                    }
                }, {
                    removeInTravel(gameSessionId, travelerId, true)
                    removeInTravel(gameSessionId, secondId, false)
                }, {
                    action()
                }, { _, _, _, _ -> })
            }.mapLeft { (playerId, reasons) ->
                logger.warn("Unable to perform travel for $playerId due to $reasons")
                InteractionException.TravelException.InsufficientResources(
                    playerId,
                    gameSessionId,
                    travelName
                )
            }.bind()
        }

    override suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        either {
            val (_, timeNeeded, moneyRange, cityCosts) = Transactor.dbQuery {
                TravelDao.getTravelData(
                    gameSessionId,
                    travelName
                ).toEither {
                    InteractionException.TravelException.CityNotFound(
                        gameSessionId,
                        travelName
                    )
                }.bind()
            }

            val reward: PosInt = moneyRange.random(PosInt.randomable)

            playerResourceService.conductEquipmentChangeOnPlayer(
                gameSessionId,
                playerId,
                PlayerEquipmentChanges(
                    money = ChangeValue(Money(0), Money(0)),
                    resources = costsToChangeValues(cityCosts),
                    time = ChangeValue(0.nonNeg, timeNeeded.toNonNegOrEmpty())
                ),
                EquipmentInternalMessage::EquipmentChangeAfterCoop
            ) { action ->
                parZip({
                    interactionProducer.sendMessage(
                        gameSessionId,
                        playerId,
                        ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart(timeout)
                    )
                }, {
                    Transactor.dbQuery {
                        EquipmentChangeQueueDao.addItemToProcessing(
                            TimestampMillis(timeout.inWholeMilliseconds),
                            gameSessionId,
                            playerId,
                            PlayerEquipmentAdditions.money(Money(reward.toNonNeg())),
                            "travel"
                        )()
                    }
                }, {
                    removeInTravel(gameSessionId, playerId, true)
                }, {
                    action()
                }, { _, _, _, _ -> })
            }.mapLeft {
                InteractionException.TravelException.InsufficientResources(
                    playerId,
                    gameSessionId,
                    travelName
                )
            }.bind()
        }

    private suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId, isTraveling: Boolean) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        if (isTraveling) {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStop
            )
        }
    }
}

fun NonEmptyMap<GameResourceName, NonNegInt>.diff(maybeResourcesDecideValues: ResourcesDecideValues): Option<ResourcesDecideValues> =
    maybeResourcesDecideValues.let { (goerId, splitRate, resourcesWanted) ->
        this.diff(resourcesWanted)
            .flatMap { ResourcesDecideValues(goerId, splitRate.invert(), it).toOption() }
    }

fun NonEmptyMap<GameResourceName, NonNegInt>.diff(resourcesWanted: NonEmptyMap<GameResourceName, NonNegInt>): Option<NonEmptyMap<GameResourceName, NonNegInt>> =
    this.padZip(resourcesWanted).map { (resourceName, values) ->
        val (maybeNeeded, maybeWanted) = values
        val needed = maybeNeeded?.value ?: 0
        val wanted = maybeWanted?.value ?: 0
        if (needed - wanted >= 0) {
            (resourceName to NonNegInt(needed - wanted)).some()
        } else {
            None
        }
    }.filterOption().toNonEmptyMapOrNone()
