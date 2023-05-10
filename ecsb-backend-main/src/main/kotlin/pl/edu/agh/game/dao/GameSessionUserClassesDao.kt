package pl.edu.agh.game.dao

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.ResourceId
import pl.edu.agh.game.domain.SessionClassDto
import pl.edu.agh.game.table.GameSessionResourceTable
import pl.edu.agh.game.table.GameSessionUserClassesTable

object GameSessionUserClassesDao {
    fun upsertClasses(classRepresentation: Map<GameClassName, SessionClassDto>, createdGameSessionId: GameSessionId) {
        val zippedClassRepresentation = classRepresentation.map { (gameClassName, sessionClassDto) ->
            Triple(
                gameClassName,
                sessionClassDto,
                createdGameSessionId
            )
        }
        val resourcesIds: List<ResourceId> =
            GameSessionResourceTable.batchInsert(zippedClassRepresentation) { (_, sessionClassDto, gameSessionId) ->
                val table = GameSessionResourceTable
                this[table.gameSessionId] = gameSessionId
                this[table.resourceName] = sessionClassDto.resourceName
            }.map { it[GameSessionResourceTable.id] }

        GameSessionUserClassesTable.batchInsert(zippedClassRepresentation.zip(resourcesIds)) { (classRepresentation, resourceId) ->
            val (gameClassName, sessionClassDto, gameSessionId) = classRepresentation
            val table = GameSessionUserClassesTable
            this[table.gameSessionId] = gameSessionId
            this[table.name] = gameClassName
            this[table.walkingAnimationIndex] = sessionClassDto.assetNumber
            this[table.producedResourceId] = resourceId
        }
    }

    fun getClasses(gameSessionId: GameSessionId): Map<GameClassName, SessionClassDto> =
        GameSessionUserClassesTable.join(
            GameSessionResourceTable,
            JoinType.INNER
        ) { GameSessionUserClassesTable.producedResourceId eq GameSessionResourceTable.id }.select {
            GameSessionUserClassesTable.gameSessionId eq gameSessionId
        }
            .associate {
                it[GameSessionUserClassesTable.name] to SessionClassDto(
                    it[GameSessionUserClassesTable.walkingAnimationIndex],
                    it[GameSessionResourceTable.resourceName]
                )
            }
}
