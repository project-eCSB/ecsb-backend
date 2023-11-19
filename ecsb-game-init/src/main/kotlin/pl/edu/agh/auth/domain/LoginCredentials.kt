package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.Sensitive

@Serializable
data class LoginCredentials(val email: String, val password: Sensitive)
