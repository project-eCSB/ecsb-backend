package pl.edu.agh.auth.domain.input

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.Sensitive

@Serializable
data class LoginRequest(val email: String, val password: Sensitive)
