package pl.edu.agh.auth.domain.output

import kotlinx.serialization.Serializable
import pl.edu.agh.auth.domain.Role

@Serializable
data class LoginResponse(val loginUserDTO: LoginUserDto, val roles: List<Role>, val jwtToken: String)
