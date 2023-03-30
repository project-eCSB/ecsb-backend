package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.auth.domain.LoginUserDTO
import pl.edu.agh.auth.domain.Roles

@Serializable
class LoginUserData(val loginUserDTO: LoginUserDTO, val roles: List<Roles>, val jwtToken: String)
