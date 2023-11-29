package pl.edu.agh.equipmentChangeQueue.table

import org.jetbrains.exposed.sql.Table
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.equipmentChangeQueue.domain.EquipmentChangeQueueId
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.longWrapper
import pl.edu.agh.utils.stringWrapper

object EquipmentChangeQueueResourceItemTable : Table("EQUIPMENT_CHANGE_QUEUE_RESOURCE_ITEM") {
    val equipmentChangeQueueId =
        longWrapper(EquipmentChangeQueueId::value, ::EquipmentChangeQueueId)("EQUIPMENT_CHANGE_QUEUE_ID")
    val resourceName = stringWrapper(GameResourceName::value, ::GameResourceName)("RESOURCE_NAME")
    val resourceValueAddition = nonNegDbWrapper("RESOURCE_VALUE_ADDITION")
}
