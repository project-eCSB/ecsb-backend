package pl.edu.agh.game.dao

import arrow.core.Option
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.toDomain
import pl.edu.agh.utils.toNonEmptyMapOrNone

object GameSessionUserClassesDao {
    fun upsertClasses(
        classRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
        createdGameSessionId: GameSessionId
    ) {
        GameSessionUserClassesTable.batchInsert(classRepresentation.toList()) { (gameClassName, gameClassResourceDto) ->
            val table = GameSessionUserClassesTable
            this[table.gameSessionId] = createdGameSessionId
            this[table.className] = gameClassName
            this[table.walkingAnimationIndex] = gameClassResourceDto.classAsset
            this[table.resourceName] = gameClassResourceDto.gameResourceName
            this[table.resourceSpriteIndex] = gameClassResourceDto.resourceAsset
            this[table.maxProduction] = gameClassResourceDto.maxProduction
            this[table.unitPrice] = gameClassResourceDto.unitPrice
            this[table.regenTime] = gameClassResourceDto.regenTime
            this[table.buyoutPrice] = gameClassResourceDto.buyoutPrice
        }
    }

    fun getClasses(gameSessionId: GameSessionId): Option<NonEmptyMap<GameClassName, GameClassResourceDto>> =
        GameSessionUserClassesTable.select {
            GameSessionUserClassesTable.gameSessionId eq gameSessionId
        }.toDomain(GameSessionUserClassesTable).toNonEmptyMapOrNone()
}
