package pl.edu.agh.game.dao

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.option
import io.ktor.http.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.*
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable
import pl.edu.agh.utils.DomainException

sealed class InteractionException(userMessage: String, internalMessage: String) : DomainException(
    HttpStatusCode.BadRequest,
    userMessage,
    internalMessage
) {
    class PlayerNotFound(gameSessionId: GameSessionId, loginUserId: LoginUserId) :
        InteractionException(
            "Dude, you are not in the game",
            "Could not find player in game session $gameSessionId for user: $loginUserId"
        )

    sealed class ProductionException(userMessage: String, internalMessage: String) :
        InteractionException(userMessage, internalMessage) {

        class UnfoundedResource(playerId: PlayerId, gameResourceName: GameResourceName) :
            ProductionException(
                "There is no such resource $gameResourceName",
                "Player $playerId tried to produce $gameResourceName which doesn't exist"
            )

        class WrongResource(playerId: PlayerId, gameResourceName: GameResourceName, gameClassName: GameClassName) :
            ProductionException(
                "Are you trying to produce $gameResourceName despite being $gameClassName?",
                "Player $playerId tried to produce $gameResourceName although he's $gameClassName"
            )

        class TooLittleMoney(playerId: PlayerId, gameResourceName: GameResourceName, money: Int, quantity: Int) :
            ProductionException(
                "You're too poor, $money is not enough to produce $quantity $gameResourceName",
                "Player $playerId has too little ($money) money to produce $quantity $gameResourceName"
            )

        class TooManyUnits(playerId: PlayerId, gameResourceName: GameResourceName, quantity: Int, maxProduction: Int) :
            ProductionException(
                "You're trying to produce $quantity $gameResourceName, but limit is $maxProduction",
                "Player $playerId wants to product $quantity $gameResourceName, but limit is $maxProduction"
            )
    }

    sealed class TravelException(userMessage: String, internalMessage: String) : InteractionException(
        userMessage,
        internalMessage
    ) {
        class InsufficientResources(
            playerId: PlayerId,
            gameSessionId: GameSessionId,
            travelName: TravelName,
            gameResourceName: String,
            playerQuantity: Int,
            cityCost: Int
        ) :
            TravelException(
                "You got insufficient $gameResourceName ($playerQuantity < $cityCost) for travel to $travelName",
                "Player $playerId in game $gameSessionId wanted to travel to $travelName, but has insufficient $gameResourceName value ($playerQuantity < $cityCost)"
            )

        class CityNotFound(
            gameSessionId: GameSessionId,
            travelName: TravelName
        ) :
            TravelException(
                "There is no such city $travelName to travel mate",
                "There is no $travelName city in game $gameSessionId"
            )
    }
}

object PlayerResourceDao {
    fun updateResources(gameSessionId: GameSessionId, playerId: PlayerId, equipmentChanges: PlayerEquipment) {
        equipmentChanges.resources.forEach { (resourceName, resourceValue) ->
            PlayerResourceTable.update({
                (PlayerResourceTable.gameSessionId eq gameSessionId) and
                    (PlayerResourceTable.playerId eq playerId) and
                    (PlayerResourceTable.resourceName eq resourceName)
            }) {
                it.update(PlayerResourceTable.value, PlayerResourceTable.value + resourceValue)
            }
        }

        GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            with(SqlExpressionBuilder) {
                it.update(GameUserTable.money, GameUserTable.money + equipmentChanges.money)
                it.update(GameUserTable.time, GameUserTable.time + equipmentChanges.time)
            }
        }
    }

    fun getUserEquipmentByLoginUserId(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<PlayerEquipment> =
        option {
            val (time, money, playerId) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId)
            }.map { Triple(it[GameUserTable.time], it[GameUserTable.money], it[GameUserTable.playerId]) }
                .firstOrNone().bind()
            val resources = PlayerResourceTable.select {
                (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
            }.map { PlayerResourceTable.toDomain(it) }

            PlayerEquipment(money, time, resources)
        }

    fun getUserEquipmentByPlayerId(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment> =
        option {
            val (time, money) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
            }.map { it[GameUserTable.time] to it[GameUserTable.money] }
                .firstOrNone().bind()
            val resources = PlayerResourceTable.select {
                (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
            }.map { PlayerResourceTable.toDomain(it) }

            PlayerEquipment(money, time, resources)
        }

    fun insertUserResources(gameSessionId: GameSessionId, playerId: PlayerId) {
        GameSessionUserClassesTable.select {
            GameSessionUserClassesTable.gameSessionId eq gameSessionId
        }.map { it[GameSessionUserClassesTable.resourceName] }
            .forEach { gameResourceName ->
                PlayerResourceTable.insert {
                    it[PlayerResourceTable.gameSessionId] = gameSessionId
                    it[PlayerResourceTable.playerId] = playerId
                    it[PlayerResourceTable.resourceName] = gameResourceName
                    it[PlayerResourceTable.value] = 0
                }
            }
    }

    fun getUsersEquipments(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> =
        option {
            val playersData: Map<PlayerId, Pair<Int, Int>> = GameUserTable.select {
                GameUserTable.playerId.inList(listOf(player1, player2))
            }.associate {
                it[GameUserTable.playerId] to (it[GameUserTable.money] to it[GameUserTable.time])
            }
            val (money1, time1) = playersData[player1].toOption().bind()
            val (money2, time2) = playersData[player2].toOption().bind()

            val resources = PlayerResourceTable.select {
                PlayerResourceTable.gameSessionId eq gameSessionId and PlayerResourceTable.playerId.inList(
                    listOf(player1, player2)
                )
            }.groupBy { it[PlayerResourceTable.playerId] }.mapValues { entry ->
                entry.value.map { PlayerResourceTable.toDomain(it) }
            }

            val player1Resources = resources[player1].toOption().bind()
            val player2Resources = resources[player2].toOption().bind()

            PlayerEquipment(money1, time1, player1Resources) to PlayerEquipment(money2, time2, player2Resources)
        }

    fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        resourceName: GameResourceName,
        quantity: Int
    ): Either<InteractionException, Unit> =
        either {
            val (playerId, gameClassName, actualMoney) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId)
            }.map { Triple(it[GameUserTable.playerId], it[GameUserTable.className], it[GameUserTable.money]) }
                .firstOrNone()
                .toEither { InteractionException.PlayerNotFound(gameSessionId, loginUserId) }.bind()

            val (foundResource, unitPrice, maxProduction) = GameSessionUserClassesTable.select {
                (GameSessionUserClassesTable.className eq gameClassName) and (GameSessionUserClassesTable.gameSessionId eq gameSessionId)
            }.map {
                Triple(
                    it[GameSessionUserClassesTable.resourceName],
                    it[GameSessionUserClassesTable.unitPrice],
                    it[GameSessionUserClassesTable.maxProduction]
                )
            }.firstOrNone()
                .toEither { InteractionException.ProductionException.UnfoundedResource(playerId, resourceName) }.bind()

            if (foundResource != resourceName) {
                InteractionException.ProductionException.WrongResource(playerId, resourceName, gameClassName).left()
                    .bind<InteractionException.ProductionException.WrongResource>()
            }

            if (actualMoney < unitPrice * quantity) {
                InteractionException.ProductionException.TooLittleMoney(playerId, resourceName, actualMoney, quantity)
                    .left().bind<InteractionException.ProductionException.TooLittleMoney>()
            }

            if (quantity > maxProduction) {
                InteractionException.ProductionException.TooManyUnits(playerId, resourceName, quantity, maxProduction)
                    .left().bind<InteractionException.ProductionException.TooManyUnits>()
            }

            PlayerResourceTable.update({
                (PlayerResourceTable.gameSessionId eq gameSessionId) and
                    (PlayerResourceTable.playerId eq playerId) and
                    (PlayerResourceTable.resourceName eq resourceName)
            }) {
                it.update(PlayerResourceTable.value, PlayerResourceTable.value + quantity)
            }

            GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
                with(SqlExpressionBuilder) {
                    it.update(GameUserTable.money, GameUserTable.money - quantity * unitPrice)
                }
            }
        }

    private data class TravelData(val minReward: Int, val maxReward: Int, val travelId: TravelId, val timeNeeded: Int?)

    fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        either {
            val (playerId, time) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId)
            }.map { it[GameUserTable.playerId] to it[GameUserTable.time] }.firstOrNone()
                .toEither { InteractionException.PlayerNotFound(gameSessionId, loginUserId) }.bind()

            val (minReward, maxReward, travelId, timeNeeded) = TravelsTable.select {
                (TravelsTable.gameSessionId eq gameSessionId) and (TravelsTable.name eq travelName)
            }.map {
                TravelData(
                    it[TravelsTable.moneyMin],
                    it[TravelsTable.moneyMax],
                    it[TravelsTable.id],
                    it[TravelsTable.timeNeeded]
                )
            }.firstOrNone()
                .toEither { InteractionException.TravelException.CityNotFound(gameSessionId, travelName) }.bind()

            val playerResources = PlayerResourceTable.select {
                (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
            }.map { PlayerResourceTable.toDomain(it) }

            val cityCosts = TravelResourcesTable.select {
                (TravelResourcesTable.travelId eq travelId)
            }.map { TravelResourcesTable.toDomain(it) }

            playerResources.zip(cityCosts).forEach {
                if (it.first.value < it.second.value) {
                    InteractionException.TravelException.InsufficientResources(
                        playerId,
                        gameSessionId,
                        travelName,
                        it.first.name.value,
                        it.first.value,
                        it.second.value
                    ).left().bind<InteractionException.TravelException.InsufficientResources>()
                }
            }

            if (timeNeeded != null && timeNeeded > time) {
                InteractionException.TravelException.InsufficientResources(
                    playerId,
                    gameSessionId,
                    travelName,
                    "time",
                    time,
                    timeNeeded
                ).left().bind<InteractionException.TravelException.InsufficientResources>()
            }

            val reward = (minReward..maxReward).random()

            cityCosts.forEach { (resourceName, resourceValue) ->
                PlayerResourceTable.update({
                    (PlayerResourceTable.gameSessionId eq gameSessionId) and
                        (PlayerResourceTable.playerId eq playerId) and
                        (PlayerResourceTable.resourceName eq resourceName)
                }) {
                    it.update(PlayerResourceTable.value, PlayerResourceTable.value - resourceValue)
                }
            }

            GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
                with(SqlExpressionBuilder) {
                    it.update(GameUserTable.money, GameUserTable.money + reward)
                    if (timeNeeded != null) {
                        it.update(GameUserTable.time, GameUserTable.time - timeNeeded)
                    }
                }
            }
        }
}
