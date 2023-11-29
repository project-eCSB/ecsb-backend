package pl.edu.agh.domain

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.utils.intWrapper

@Serializable
@JvmInline
value class LoginUserId(val value: Int)

fun Table.loginUserId(name: String): Column<LoginUserId> = intWrapper(LoginUserId::value, ::LoginUserId)(name)
