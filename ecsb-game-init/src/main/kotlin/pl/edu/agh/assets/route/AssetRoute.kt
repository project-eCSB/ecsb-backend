package pl.edu.agh.assets.route

import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetDto
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.assets.service.SavedAssetsService
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getLoggedUser
import pl.edu.agh.utils.Utils.getBody
import pl.edu.agh.utils.Utils.getParam
import pl.edu.agh.utils.Utils.handleOutput
import pl.edu.agh.utils.Utils.handleOutputFile
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger
import java.io.File

object AssetRoute {
    fun Application.configureGameAssetsRoutes() {
        val logger = getLogger(Application::class.java)
        val savedAssetsService: SavedAssetsService by inject()

        routing {
            route("assets") {
                authenticate(Token.LOGIN_USER_TOKEN, Role.ADMIN) {
                    post {
                        handleOutput(call) {
                            either {
                                val (_, _, loginUserId) = getLoggedUser(call)
                                val fileBody = getBody<ByteArray>(call).bind()

                                val name = getParam("fileName").bind()
                                val fileType = FileType.MAP

                                // TODO add parser with validation
//                            val classes = NonEmptyList<ClassName>

                                savedAssetsService.saveNewFile(name, loginUserId, fileType, fileBody)
                                    .mapLeft {
                                        logger.warn("Couldn't save file", it)
                                        HttpStatusCode.BadRequest to "Couldn't save file"
                                    }.bind()
                            }.responsePair(SavedAssetsId.serializer())
                        }
                    }
                    get {
                        handleOutput(call) {
                            either {
                                val (_, _, loginUserId) = getLoggedUser(call)
                                val fileType = getParam("fileType").bind()
                                savedAssetsService.getAllMapAssets(loginUserId, FileType.valueOf(fileType)).right()
                                    .bind()
                            }.responsePair(SavedAssetDto.serializer())
                        }
                    }
                }
                authenticate(Token.LOGIN_USER_TOKEN, Role.ADMIN, Role.USER) {
                    get("/{savedAssetId}") {
                        handleOutputFile(call) {
                            either {
                                val savedAssetsId = getParam("savedAssetId", ::SavedAssetsId).bind()
                                val (_, _, loginUserId) = getLoggedUser(call)

                                logger.info("User $loginUserId requested file with id $savedAssetsId")
                                val path = savedAssetsService.getPath(savedAssetsId).bind()
                                File(path)
                            }
                        }
                    }
                }
            }
        }
    }
}
