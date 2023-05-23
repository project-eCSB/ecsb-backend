package pl.edu.agh.assets.route

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.right
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
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameClassName
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
                                    Either.catch { FileType.valueOf(it) }
                                        .mapLeft { HttpStatusCode.BadRequest to "Unable to parse file type" }
                                }.bind()

                                when (fileType) {
                                    FileType.PNG -> savedAssetsService.saveImage(name, loginUserId, fileBody)
                                    FileType.MAP -> {
                                        val assetId = getParam("tilesAssetId", ::SavedAssetsId).bind()
                                        val characterAssetsId = getParam("charactersAssetId", ::SavedAssetsId).bind()

                                        val coordinates = Coordinates(3, 3)

                                        val mapAdditionalData =
                                            MapAdditionalData(
                                                assetId,
                                                characterAssetsId,
                                                MapAssetDataDto(
                                                    lowLevelTrips = nonEmptyListOf(coordinates),
                                                    mediumLevelTrips = nonEmptyListOf(coordinates),
                                                    highLevelTrips = nonEmptyListOf(coordinates),
                                                    startingPoint = coordinates,
                                                    professionWorkshops = mapOf(
                                                        GameClassName("test class") to nonEmptyListOf(
                                                            coordinates
                                                        )
                                                    )
                                                )
                                            )

                                        savedAssetsService.saveMap(name, loginUserId, fileBody, mapAdditionalData)
                                    }
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
                                val fileType = getParam("fileType").bind()
                                savedAssetsService.getAllMapAssets(loginUserId, FileType.valueOf(fileType)).right()
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
