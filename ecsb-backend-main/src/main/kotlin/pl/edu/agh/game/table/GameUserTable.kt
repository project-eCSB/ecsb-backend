package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.loginUserId
import pl.edu.agh.domain.*
import pl.edu.agh.game.domain.GameUserDto
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper

object GameUserTable : Table("GAME_USER") {
    val loginUserId: Column<LoginUserId> = loginUserId("LOGIN_USER_ID")
    val playerId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("NAME")
    val className: Column<GameClassName> = stringWrapper(GameClassName::value, ::GameClassName)("CLASS_NAME")
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val money: Column<Money> = longWrapper(Money::value, ::Money)("MONEY")
    val time: Column<NonNegInt> = nonNegDbWrapper("TIME")
    val inGame: Column<Boolean> = bool("IN_GAME").default(true)
    val busyStatus: Column<InteractionStatus> =
        nullableStringWrapper(InteractionStatus::toDB, InteractionStatus::fromDB)("BUSY_STATUS")

    fun toDomain(resultRow: ResultRow): GameUserDto = GameUserDto(
        resultRow[gameSessionId],
        resultRow[playerId],
        resultRow[loginUserId],
        resultRow[className],
        resultRow[inGame]
    )
}
