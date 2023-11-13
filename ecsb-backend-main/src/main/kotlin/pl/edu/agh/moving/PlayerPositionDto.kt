package pl.edu.agh.moving

import arrow.core.Option
import arrow.core.some
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.*
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.moving.domain.Coordinates
import pl.edu.agh.moving.domain.Direction
import pl.edu.agh.moving.domain.PlayerPosition

@Serializable
data class PlayerPositionDto(
    val id: PlayerId,
    val coords: Coordinates,
    val direction: Direction,
    val gameClassName: GameClassName,
    val isActive: Boolean
) {

    fun toPlayerPosition(): PlayerPosition =
        PlayerPosition(id, coords, direction)

    fun toPlayerPositionWithClassIfActive(): Option<PlayerPositionWithClass> =
        PlayerPositionWithClass(gameClassName, toPlayerPosition()).some().filter { _ -> isActive }

    companion object {
        fun invoke(playerPosition: PlayerPosition, gameClassName: GameClassName): PlayerPositionDto =
            PlayerPositionDto(playerPosition.id, playerPosition.coords, playerPosition.direction, gameClassName, true)
    }
}
