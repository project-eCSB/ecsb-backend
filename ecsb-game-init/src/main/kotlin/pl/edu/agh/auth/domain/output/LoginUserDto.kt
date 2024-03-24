package pl.edu.agh.auth.domain.output

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.LoginUserId

@Serializable
data class LoginUserDto(val id: LoginUserId, val email: String)
