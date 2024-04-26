package pl.edu.agh.auth.service

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.either
import arrow.core.right
import arrow.core.toOption
import io.ktor.http.*
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.HtmlEmail
import org.jetbrains.exposed.sql.batchInsert
import pl.edu.agh.auth.dao.UserDao
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.input.LoginRequest
import pl.edu.agh.auth.domain.output.LoginResponse
import pl.edu.agh.auth.table.UserRolesTable
import pl.edu.agh.utils.DomainException
import pl.edu.agh.utils.Transactor
import java.util.*

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

    object SingleQuoteIdentified : RegisterException(
        "Password could not contain single quote",
        "Password contained single quote while registering user"
    )

    object LackOfVerifyParameters : RegisterException(
        "Lack of email or token in verify query",
        "Lack of email or token in verify query"
    )

    class WrongVerificationToken(email: String) : RegisterException(
        "Wrong verification token for mail: $email",
        "Wrong verification token for mail: $email"
    )
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

    class SingleQuoteIdentified(email: String) : LoginException(
        "Password could not contain single quote",
        "Password contained single quote while logging in user with email: $email"
    )
}

interface AuthService {
    suspend fun signUpNewUser(loginRequest: LoginRequest): Either<RegisterException, String>
    suspend fun verifyNewUser(email: String?, token: String?): Either<RegisterException, String>
    suspend fun signInUser(loginRequest: LoginRequest): Either<LoginException, LoginResponse>
}

class AuthServiceImpl(private val tokenCreationService: TokenCreationService) : AuthService {

    override suspend fun signUpNewUser(loginRequest: LoginRequest): Either<RegisterException, String> =
        either {
            (if (loginRequest.password.value.length >= 6) {
                Right(Unit)
            } else {
                Left(RegisterException.PasswordTooShort)
            }).bind()
            (if (loginRequest.password.value.contains("'")) {
                Left(RegisterException.SingleQuoteIdentified)
            } else {
                Right(Unit)
            }).bind()

            val verificationToken = UUID.randomUUID().toString()
            Transactor.dbQuery {
                UserDao.findUserByEmail(loginRequest.email)
                    .map { RegisterException.EmailAlreadyExists(loginRequest.email) }.toEither { }.swap().bind()

                UserDao.insertNewUser(loginRequest, verificationToken)
                val verificationEmail = composeVerificationEmail(loginRequest.email, verificationToken)
                sendVerificationEmail(loginRequest.email, verificationEmail)
                "Verification mail sent".right().bind()
            }
        }

    override suspend fun verifyNewUser(email: String?, token: String?): Either<RegisterException, String> =
        either {
            val safeEmail = email.toOption()
                .toEither { RegisterException.LackOfVerifyParameters }
                .bind()
            val safeToken = token.toOption()
                .toEither { RegisterException.LackOfVerifyParameters }
                .bind()

            Transactor.dbQuery {
                val newId = UserDao.verifyUser(safeEmail, safeToken)
                    .toEither { RegisterException.WrongVerificationToken(safeEmail) }
                    .bind()

                val basicRoles = listOf(Role.USER)
                UserRolesTable.batchInsert(basicRoles) { role ->
                    this[UserRolesTable.roleId] = role.roleId
                    this[UserRolesTable.userId] = newId
                }
            }
            "Email $safeEmail verified, go back to login page"
        }

    private fun composeVerificationEmail(email: String, verificationToken: String): String {
        val htmlContent = AuthService::class.java.getResource("/mail_template.html")!!.readText()
        val verificationLink = "https://ecsb-dev.mooo.com/api/init/verify?token=$verificationToken&mail=$email"
        return htmlContent.replace("\$email", email).replace("\$verificationLink", verificationLink)
    }

    private fun sendVerificationEmail(mail: String, verificationEmail: String) {
        val email = HtmlEmail()
        email.hostName = "smtp.googlemail.com"
        email.setSmtpPort(465)
        email.setAuthenticator(DefaultAuthenticator("no.reply.ecsb@gmail.com", "dgrgrnnkjugscyev"))
        email.isSSLOnConnect = true
        email.setFrom("no.reply.ecsb@gmail.com")
        email.subject = "eCSB mail verification"
        email.addTo(mail)
        email.setHtmlMsg(verificationEmail)
        email.send()
    }

    override suspend fun signInUser(loginRequest: LoginRequest): Either<LoginException, LoginResponse> =
        either {
            (if (loginRequest.password.value.contains("'")) {
                Left(LoginException.SingleQuoteIdentified(loginRequest.email))
            } else {
                Right(Unit)
            }).bind()
            val (loginDto, userRoles) = Transactor.dbQuery {
                UserDao.findUserByEmail(loginRequest.email)
                    .toEither { LoginException.UserNotFound(loginRequest.email) }.bind()

                val loginDto = UserDao.verifyCredentials(loginRequest.email, loginRequest.password)
                    .toEither { LoginException.WrongPassword(loginRequest.email) }.bind()

                val userRoles = UserDao.getUserRoles(loginDto.id)
                loginDto to userRoles
            }

            LoginResponse(
                id = loginDto.id,
                email = loginDto.email,
                roles = userRoles,
                jwtToken = tokenCreationService.createToken(loginRequest.email, userRoles, loginDto.id)
            ).right().bind()
        }
}
