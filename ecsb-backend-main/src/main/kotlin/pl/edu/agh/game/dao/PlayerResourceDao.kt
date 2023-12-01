package pl.edu.agh.game.dao

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.mapOrAccumulate
import arrow.core.raise.option
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.equipment.domain.PlayerEquipment
import pl.edu.agh.game.domain.UpdatedTokens
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable
import pl.edu.agh.time.table.PlayerTimeTokenTable
import pl.edu.agh.time.table.TimeTokensUsedInfo
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.literal
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

object PlayerResourceDao {
    private val logger by LoggerDelegate()
    fun updateResources(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        equipmentChanges: PlayerEquipmentChanges
    ): DB<Either<NonEmptyList<String>, UpdatedTokens>> = {
        val allResourcesToBeUpdated: Map<GameResourceName, Ior<NonNegInt, NonNegInt>> =
            equipmentChanges.resources.map { (resourceName, change) ->
                val (addition, deletion) = change
                Ior.fromNullables(addition, deletion).toOption().map {
                    resourceName to it
                }
            }.flattenOption().toMap()

        val resourcesValuesClause =
            allResourcesToBeUpdated.fold(CaseWhen<NonNegInt>(null)) { initialCase, (resourceName, iorChange) ->
                val updatedValue = iorChange.fold(
                    { left -> PlayerResourceTable.value + left },
                    { right -> PlayerResourceTable.value - right },
                    { left, right -> PlayerResourceTable.value + left - right }
                )

                val updatedValueWithCase = case(null).When(GreaterEqOp(updatedValue, 0.nonNeg.literal()), updatedValue)
                    .Else(PlayerResourceTable.value)

                initialCase.When(
                    PlayerResourceTable.resourceName eq resourceName,
                    updatedValueWithCase
                )
            }.let {
                CaseWhenElse(it, PlayerResourceTable.value)
            }

        either {
            val oldPlayerResourceTable = PlayerResourceTable.alias("old_player_resource")
            val updateResult = PlayerResourceTable.updateReturning(
                {
                    (PlayerResourceTable.gameSessionId eq gameSessionId) and
                            (PlayerResourceTable.playerId eq playerId) and
                            (PlayerResourceTable.resourceName.inList(allResourcesToBeUpdated.keys))
                },
                from = oldPlayerResourceTable,
                joinColumns = listOf(
                    PlayerResourceTable.gameSessionId,
                    PlayerResourceTable.playerId,
                    PlayerResourceTable.resourceName
                ),
                updateObjects = UpdateObject(PlayerResourceTable.value, resourcesValuesClause),
                returningNew = mapOf("resource_name" to PlayerResourceTable.resourceName)
            )
            mapOrAccumulate(updateResult) { (returningNew, returningBoth) ->
                option {
                    val resourceName = returningNew["resource_name"].toOption().bind()
                    val valueChanges =
                        returningBoth[PlayerResourceTable.value.name].toOption().bind()
                    val changes = allResourcesToBeUpdated[resourceName].toOption().bind()

                    changes.fold(
                        { _ -> true },
                        { _ -> (valueChanges.after as NonNegInt) < (valueChanges.before as NonNegInt) },
                        { addition, deletion -> if (addition >= deletion) true else (valueChanges.after as NonNegInt) < (valueChanges.before as NonNegInt) }
                    )
                }
                    .toEither { "Internal server error (Error with query execution)" }
                    .flatMap { checkValue ->
                        if (!checkValue) {
                            "Too little ${returningNew["resource_name"]}".left()
                        } else {
                            Unit.right()
                        }
                    }
                    .bind()
            }

            val timeDiff = equipmentChanges.time.map(NonNegInt::value).diff()
            val timeTokensUsedInfo = (if (timeDiff > 0) {
                nonEmptyListOf("Cannot add time here").left()
            } else if (timeDiff == 0) {
                TimeTokensUsedInfo.empty.right()
            } else {
                val timeTokensUsedInfo =
                    PlayerTimeTokenTable.decreasePlayerTimeTokensQuery(gameSessionId, playerId, PosInt(-timeDiff))

                if (timeTokensUsedInfo.amountUsed.value != -timeDiff) {
                    nonEmptyListOf("Not enough time").left()
                } else {
                    timeTokensUsedInfo.right()
                }
            }).bind()

            val updatedMoney = equipmentChanges.money.addToColumn(GameUserTable.money)
            val updatedMoneyWithCase =
                case(null).When(GreaterEqOp(updatedMoney, 0.nonNeg.literal()), updatedMoney).Else(GameUserTable.money)

            val oldGameUser = GameUserTable.alias("old_game_user")
            GameUserTable.updateReturning(
                {
                    (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
                },
                from = oldGameUser,
                joinColumns = listOf(GameUserTable.gameSessionId, GameUserTable.playerId),
                updateObjects = UpdateObject(GameUserTable.money, updatedMoneyWithCase),
                returningNew = mapOf<String, Column<String>>()
            ).map { (_, returningBoth) ->
                either<NonEmptyList<String>, Unit> {
                    val maybeMoneyChanges = returningBoth[GameUserTable.money.name].toOption()
                    ensure(maybeMoneyChanges.isSome()) { nonEmptyListOf("Couldn't get money from query") }
                    maybeMoneyChanges.map { moneyChanges ->
                        ensure(
                            !(moneyChanges.before == moneyChanges.after && equipmentChanges.money.map(Money::value).diff() != 0)
                        ) {
                            nonEmptyListOf("Too little money")
                        }
                    }
                }.onLeft { logger.error("Couldn't do this exchange because $it") }
                    .onLeft { logger.error("Couldn't get info about money from: \n$returningBoth") }
                    .onRight { logger.info("Successfully updated money") }
            }.bindAll()

            UpdatedTokens(timeTokensUsedInfo.timeTokensUsed)
        }.onLeft { rollback() }.onLeft { logger.error("Couldn't update equipment due to $it") }
            .onRight { logger.info("Successfully updated $playerId equipment  in $gameSessionId") }
    }

    fun getUserEquipment(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment> =
        getUsersEquipments(gameSessionId, listOf(playerId))[playerId].toOption()

    fun getUsersEquipments(
        gameSessionId: GameSessionId,
        players: List<PlayerId>
    ): Map<PlayerId, PlayerEquipment> {
        val playersBasicEquipment = GameUserTable
            .slice(GameUserTable.playerId, GameUserTable.money)
            .select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId inList players)
            }.associate {
                it[GameUserTable.playerId] to it[GameUserTable.money]
            }

        val resources = PlayerResourceTable.slice(
            PlayerResourceTable.playerId,
            PlayerResourceTable.resourceName,
            PlayerResourceTable.value
        ).select {
            (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId inList players)
        }.groupBy(
            { it[PlayerResourceTable.playerId] },
            { it[PlayerResourceTable.resourceName] to it[PlayerResourceTable.value] }
        ).mapValues { (_, value) ->
            value.toNonEmptyMapOrNone()
        }.filterOption()

        return playersBasicEquipment.zip(resources) { _, money, maybeResources ->
            PlayerEquipment(money, maybeResources)
        }
    }

    fun insertUserResources(gameSessionId: GameSessionId, playerId: PlayerId) {
        GameSessionUserClassesTable
            .slice(GameSessionUserClassesTable.resourceName)
            .select {
                GameSessionUserClassesTable.gameSessionId eq gameSessionId
            }.map { it[GameSessionUserClassesTable.resourceName] }.forEach { gameResourceName ->
                PlayerResourceTable.insert {
                    it[PlayerResourceTable.gameSessionId] = gameSessionId
                    it[PlayerResourceTable.playerId] = playerId
                    it[PlayerResourceTable.resourceName] = gameResourceName
                    it[PlayerResourceTable.value] = 0.nonNeg
                }
            }
    }

    fun getPlayerWorkshopData(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<Triple<GameResourceName, PosInt, PosInt>> = GameUserTable.join(
        GameSessionUserClassesTable,
        JoinType.INNER
    ) {
        (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and (GameUserTable.className eq GameSessionUserClassesTable.className)
    }.slice(
        GameSessionUserClassesTable.resourceName,
        GameSessionUserClassesTable.unitPrice,
        GameSessionUserClassesTable.maxProduction
    ).select {
        (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
    }.firstOrNone().map {
        Triple(
            it[GameSessionUserClassesTable.resourceName],
            it[GameSessionUserClassesTable.unitPrice],
            it[GameSessionUserClassesTable.maxProduction]
        )
    }
}
