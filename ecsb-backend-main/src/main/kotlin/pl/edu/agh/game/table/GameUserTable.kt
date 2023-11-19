package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.loginUserId
import pl.edu.agh.domain.*
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameUserDto
import pl.edu.agh.utils.*
import java.time.Instant

object GameUserTable : Table("GAME_USER"), Domainable<GameUserDto> {
    val loginUserId: Column<LoginUserId> = loginUserId("LOGIN_USER_ID")
    val playerId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("NAME")
    val className: Column<GameClassName> = stringWrapper(GameClassName::value, ::GameClassName)("CLASS_NAME")
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val money: Column<Money> = longWrapper(Money::value, ::Money)("MONEY")
    val inGame: Column<Boolean> = bool("IN_GAME")
    val busyStatus: Column<InteractionStatus> =
        nullableStringWrapper(InteractionStatus::toDB, InteractionStatus::fromDB)("BUSY_STATUS")
    val createdAt: Column<Instant> = timestampWithTimeZone("CREATED_AT")

    override fun toDomain(resultRow: ResultRow): GameUserDto = GameUserDto(
        resultRow[gameSessionId],
        resultRow[playerId],
        resultRow[loginUserId],
        resultRow[className],
        resultRow[inGame]
    )

    override val domainColumns: List<Expression<*>> = listOf(gameSessionId, playerId, loginUserId, className, inGame)
}
