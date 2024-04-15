package pl.edu.agh.auth.domain.output

import kotlinx.serialization.Serializable
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.domain.LoginUserId

@Serializable
data class LoginResponse(val id: LoginUserId, val email: String, val roles: List<Role>, val jwtToken: String)
