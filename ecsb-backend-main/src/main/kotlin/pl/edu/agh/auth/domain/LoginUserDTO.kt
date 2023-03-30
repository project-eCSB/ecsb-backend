package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable

@Serializable
data class LoginUserDTO(val id: LoginUserId, val email: String)
