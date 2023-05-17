package pl.edu.agh.game.dao

import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.game.table.GameSessionUserClassesTable

object GameSessionUserClassesDao {
    fun upsertClasses(classRepresentation: List<GameClassResourceDto>, createdGameSessionId: GameSessionId) {
        GameSessionUserClassesTable.batchInsert(classRepresentation) {
            val table = GameSessionUserClassesTable
            this[table.gameSessionId] = createdGameSessionId
            this[table.className] = it.gameClassName
            this[table.walkingAnimationIndex] = it.classAsset
            this[table.resourceName] = it.gameResourceName
            this[table.resourceSpriteIndex] = it.resourceAsset
        }
    }

    fun getClasses(gameSessionId: GameSessionId): List<GameClassResourceDto> =
        GameSessionUserClassesTable.select {
            GameSessionUserClassesTable.gameSessionId eq gameSessionId
        }.map { GameSessionUserClassesTable.toDomain(it) }
}
