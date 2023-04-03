package pl.edu.agh.auth.domain

import kotlinx.serialization.Serializable

@Serializable
class LoginUserData(val loginUserDTO: LoginUserDTO, val roles: List<Role>, val jwtToken: String)
