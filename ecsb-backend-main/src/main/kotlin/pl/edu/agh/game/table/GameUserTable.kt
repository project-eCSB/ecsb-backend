package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.loginUserId
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.GameUserDto
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object GameUserTable : Table("GAME_USER") {
    val loginUserId: Column<LoginUserId> = loginUserId("LOGIN_USER_ID")
    val playerId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("NAME")
    val className: Column<GameClassName> = stringWrapper(GameClassName::value, ::GameClassName)("CLASS_NAME")
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")

    fun toDomain(resultRow: ResultRow): GameUserDto = GameUserDto(
        resultRow[gameSessionId], resultRow[playerId], resultRow[loginUserId], resultRow[className]
    )
}