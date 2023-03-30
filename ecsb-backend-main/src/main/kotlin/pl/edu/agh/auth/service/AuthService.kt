package pl.edu.agh.auth.service

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.right
import io.ktor.http.*
import org.jetbrains.exposed.sql.batchInsert
import pl.edu.agh.auth.dao.UserDao
import pl.edu.agh.auth.domain.LoginUserBasicData
import pl.edu.agh.auth.domain.LoginUserDTO
import pl.edu.agh.auth.domain.LoginUserData
import pl.edu.agh.auth.domain.Roles
import pl.edu.agh.auth.table.UserRolesTable
import pl.edu.agh.utils.DomainException

sealed class RegisterException(userMessage: String, internalMessage: String) : DomainException(
    HttpStatusCode.BadRequest, userMessage, internalMessage
) {
    class EmailAlreadyExists(email: String) :
        RegisterException("Email already exists", "Email already exists while registering user with email: $email")

    object PasswordTooShort : RegisterException("Password is too short", "Password is too short while registering user")
}

sealed class LoginException(userMessage: String, internalMessage: String) : DomainException(
    HttpStatusCode.BadRequest, userMessage, internalMessage
) {
    class UserNotFound(email: String) :
        LoginException("Wrong login or password", "User not found while logging in user with email: $email")

    class WrongPassword(email: String) :
        LoginException("Wrong login or password", "Wrong password while logging in user with email: $email")
}

interface AuthService {
    suspend fun signUpNewUser(loginUserBasicData: LoginUserBasicData): Either<RegisterException, LoginUserData>
    suspend fun signInUser(loginUserBasicData: LoginUserBasicData): Either<LoginException, LoginUserData>
}


class AuthServiceImpl(private val tokenCreationService: TokenCreationService) : AuthService {

    override suspend fun signUpNewUser(loginUserBasicData: LoginUserBasicData): Either<RegisterException, LoginUserData> =
        either {
            Either.conditionally(loginUserBasicData.password.length > 8,
                ifFalse = { RegisterException.PasswordTooShort },
                ifTrue = { }).bind()

            UserDao.findUserByEmail(loginUserBasicData.email)
                .map { RegisterException.EmailAlreadyExists(loginUserBasicData.email) }
                .toEither { }.swap().bind()

            val newId = UserDao.insertNewUser(loginUserBasicData)
            val basicRoles = listOf(Roles.USER)

            UserRolesTable.batchInsert(basicRoles) { role ->
                this[UserRolesTable.roleId] = role.id
                this[UserRolesTable.userId] = newId
            }

            LoginUserData(
                loginUserDTO = LoginUserDTO(id = newId, email = loginUserBasicData.email),
                roles = basicRoles,
                jwtToken = tokenCreationService.createToken(loginUserBasicData.email, basicRoles, newId)
            ).right().bind()
        }

    override suspend fun signInUser(loginUserBasicData: LoginUserBasicData): Either<LoginException, LoginUserData> =
        either {
            UserDao.findUserByEmail(loginUserBasicData.email)
                .toEither { LoginException.UserNotFound(loginUserBasicData.email) }.bind()

            val user = UserDao.tryLogin(loginUserBasicData.email, loginUserBasicData.password)
                .toEither { LoginException.WrongPassword(loginUserBasicData.email) }.bind()

            val userRoles = UserDao.getUserRoles(user.id)

            LoginUserData(
                loginUserDTO = user,
                roles = userRoles,
                jwtToken = tokenCreationService.createToken(loginUserBasicData.email, userRoles, user.id)
            ).right().bind()
        }
}