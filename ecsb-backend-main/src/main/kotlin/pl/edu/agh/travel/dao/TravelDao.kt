package pl.edu.agh.travel.dao

import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.travel.domain.GameTravelsInputDto
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable

object TravelDao {

    fun insertTravel(validatedTravel: GameTravelsInputDto, travelResources: Map<GameResourceName, Int>): TravelId {
        val travelId = insertTravel(validatedTravel)

        batchInsertDto(travelId, travelResources.toList())

        return travelId
    }

    private fun insertTravel(validatedTravel: GameTravelsInputDto): TravelId = TravelsTable.insert {
        it[TravelsTable.gameSessionId] = validatedTravel.gameSessionId
        it[TravelsTable.travelType] = validatedTravel.travelType
        it[TravelsTable.name] = validatedTravel.name
        it[TravelsTable.timeNeeded] = validatedTravel.timeNeeded.orNull()
        it[TravelsTable.moneyMin] = validatedTravel.moneyRange.from.toInt()
        it[TravelsTable.moneyMax] = validatedTravel.moneyRange.to.toInt()
    }[TravelsTable.id]

    private fun batchInsertDto(travelId: TravelId, travelResources: List<Pair<GameResourceName, Int>>) {
        TravelResourcesTable.batchInsert(travelResources) { (resourceName, value) ->
            this[TravelResourcesTable.travelId] = travelId
            this[TravelResourcesTable.classResourceName] = resourceName
            this[TravelResourcesTable.value] = value
        }
    }
}
