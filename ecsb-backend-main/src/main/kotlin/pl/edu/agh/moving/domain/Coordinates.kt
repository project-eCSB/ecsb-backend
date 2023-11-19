package pl.edu.agh.moving.domain

import kotlinx.serialization.Serializable

@Serializable
data class Coordinates(val x: Int, val y: Int) {

    fun isInRange(center: Coordinates, range: Int): Boolean =
        (center.x + range >= x && x >= center.x - range) && (center.y + range >= y && y >= center.y - range)
}
