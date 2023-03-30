package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable

@Serializable
data class LoginUserBasicData(val email: String, val password: String)