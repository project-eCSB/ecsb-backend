package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.LoginUserId

@Serializable
data class LoginUserDTO(val id: LoginUserId, val email: String)
