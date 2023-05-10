package pl.edu.agh.game.dao

import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.AssetNumber
import pl.edu.agh.game.table.GameSessionUserClassesTable

object GameSessionUserClassesDao {
    fun upsertClasses(classRepresentation: Map<GameClassName, AssetNumber>, createdGameSessionId: GameSessionId) {
        val zippedClassRepresentation = classRepresentation.map { (gameClassName, assetNumber) ->
            Triple(
                gameClassName, assetNumber, createdGameSessionId
            )
        }
        GameSessionUserClassesTable.batchInsert(zippedClassRepresentation) { (className, assetNumber, gameSessionId) ->
            val table = GameSessionUserClassesTable
            this[table.gameSessionId] = gameSessionId
            this[table.name] = className
            this[table.walkingAnimationIndex] = assetNumber
        }
    }

    fun getClasses(gameSessionId: GameSessionId): Map<GameClassName, AssetNumber> = GameSessionUserClassesTable.select {
        GameSessionUserClassesTable.gameSessionId eq gameSessionId
    }.associate { it[GameSessionUserClassesTable.name] to it[GameSessionUserClassesTable.walkingAnimationIndex] }
}