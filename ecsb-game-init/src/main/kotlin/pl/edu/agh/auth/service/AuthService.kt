package pl.edu.agh.auth.service

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.*
import org.jetbrains.exposed.sql.batchInsert
import pl.edu.agh.auth.dao.UserDao
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.input.LoginRequest
import pl.edu.agh.auth.domain.output.LoginResponse
import pl.edu.agh.auth.domain.output.LoginUserDto
import pl.edu.agh.auth.table.UserRolesTable
import pl.edu.agh.utils.DomainException
import pl.edu.agh.utils.Transactor

sealed class RegisterException(
    userMessage: String,
    internalMessage: String
) : DomainException(
    HttpStatusCode.BadRequest,
    userMessage,
    internalMessage
) {
    class EmailAlreadyExists(email: String) :
        RegisterException("Email already exists", "Email already exists while registering user with email: $email")

    object PasswordTooShort : RegisterException("Password is too short", "Password is too short while registering user")
}

sealed class LoginException(
    userMessage: String,
    internalMessage: String
) : DomainException(
    HttpStatusCode.BadRequest,
    userMessage,
    internalMessage
) {
    class UserNotFound(email: String) :
        LoginException("Wrong login or password", "User not found while logging in user with email: $email")

    class WrongPassword(email: String) :
        LoginException("Wrong login or password", "Wrong password while logging in user with email: $email")
}

interface AuthService {
    suspend fun signUpNewUser(loginRequest: LoginRequest): Either<RegisterException, LoginResponse>
    suspend fun signInUser(loginRequest: LoginRequest): Either<LoginException, LoginResponse>
}

class AuthServiceImpl(private val tokenCreationService: TokenCreationService) : AuthService {

    override suspend fun signUpNewUser(loginRequest: LoginRequest): Either<RegisterException, LoginResponse> =
        either {
            (if (loginRequest.password.value.length >= 6) Right(Unit) else Left(RegisterException.PasswordTooShort)).bind()

            val (newId, basicRoles) = Transactor.dbQuery {
                UserDao.findUserByEmail(loginRequest.email)
                    .map { RegisterException.EmailAlreadyExists(loginRequest.email) }.toEither { }.swap().bind()

                val newId = UserDao.insertNewUser(loginRequest)
                val basicRoles = listOf(Role.USER)

                UserRolesTable.batchInsert(basicRoles) { role ->
                    this[UserRolesTable.roleId] = role.roleId
                    this[UserRolesTable.userId] = newId
                }

                newId to basicRoles
            }

            LoginResponse(
                loginUserDTO = LoginUserDto(id = newId, email = loginRequest.email),
                roles = basicRoles,
                jwtToken = tokenCreationService.createToken(loginRequest.email, basicRoles, newId)
            ).right().bind()
        }

    override suspend fun signInUser(loginRequest: LoginRequest): Either<LoginException, LoginResponse> =
        either {
            val (user, userRoles) = Transactor.dbQuery {
                UserDao.findUserByEmail(loginRequest.email)
                    .toEither { LoginException.UserNotFound(loginRequest.email) }.bind()

                val user = UserDao.verifyCredentials(loginRequest.email, loginRequest.password)
                    .toEither { LoginException.WrongPassword(loginRequest.email) }.bind()

                val userRoles = UserDao.getUserRoles(user.id)
                user to userRoles
            }

            LoginResponse(
                loginUserDTO = user,
                roles = userRoles,
                jwtToken = tokenCreationService.createToken(loginRequest.email, userRoles, user.id)
            ).right().bind()
        }
}
