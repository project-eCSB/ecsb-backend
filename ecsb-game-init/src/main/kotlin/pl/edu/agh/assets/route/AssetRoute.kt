package pl.edu.agh.assets.route

import arrow.core.*
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.assets.domain.*
import pl.edu.agh.assets.service.SavedAssetsService
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getLoggedUser
import pl.edu.agh.tiled.service.JsonParser
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
                                val fileType = getParam("fileType").flatMap {
                                    Either.catch { FileType.fromString(it) }
                                        .mapLeft { HttpStatusCode.BadRequest to "Unable to parse file type" }
                                }.bind()

                                logger.info("User $loginUserId is trying to add file $name of type $fileType")

                                when (fileType) {
                                    FileType.MAP -> {
                                        val assetId = getParam("tilesAssetId", ::SavedAssetsId).bind()
                                        val characterAssetsId = getParam("charactersAssetId", ::SavedAssetsId).bind()

                                        val parserData = JsonParser.parse(fileBody.decodeToString())
                                            .mapLeft { HttpStatusCode.BadRequest to it.message() }.bind()

                                        val mapAdditionalData =
                                            MapAdditionalData(
                                                assetId,
                                                characterAssetsId,
                                                parserData
                                            )

                                        savedAssetsService.saveMap(name, loginUserId, fileBody, mapAdditionalData)
                                    }
                                    else -> savedAssetsService.saveBasicAsset(name, loginUserId, fileBody, fileType)
                                }
                                    .mapLeft {
                                        logger.error("Couldn't save file", it)
                                        HttpStatusCode.BadRequest to "Couldn't save file"
                                    }.bind()
                            }.responsePair(SavedAssetsId.serializer())
                        }
                    }
                    get {
                        handleOutput(call) {
                            either {
                                val (_, _, loginUserId) = getLoggedUser(call)
                                val fileType = getParam("fileType").flatMap {
                                    Either.catch { FileType.fromString(it) }
                                        .mapLeft { HttpStatusCode.BadRequest to "Unable to parse file type" }
                                }.bind()

                                logger.info("User requested all assets of type $fileType")

                                savedAssetsService.getAllAssets(loginUserId, fileType).right()
                                    .bind()
                            }.responsePair(SavedAssetDto.serializer())
                        }
                    }
                }
                authenticate(Token.LOGIN_USER_TOKEN, Role.ADMIN, Role.USER) {
                    get("/config/{savedAssetId}") {
                        handleOutput(call) {
                            either {
                                val savedAssetsId = getParam("savedAssetId", ::SavedAssetsId).bind()
                                val (_, _, loginUserId) = getLoggedUser(call)

                                logger.info("User $loginUserId requested asset config with id $savedAssetsId")
                                savedAssetsService.findMapConfig(savedAssetsId).bind()
                            }.responsePair(MapAssetView.serializer())
                        }
                    }
                }
                get("/{savedAssetId}") {
                    handleOutputFile(call) {
                        either {
                            val savedAssetsId = getParam("savedAssetId", ::SavedAssetsId).bind()

                            logger.info("User requested file with id $savedAssetsId")
                            val path = savedAssetsService.getPath(savedAssetsId).bind()
                            File(path)
                        }
                    }
                }
            }
        }
    }
}
