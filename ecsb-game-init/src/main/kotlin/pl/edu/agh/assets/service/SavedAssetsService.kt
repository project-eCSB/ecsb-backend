package pl.edu.agh.assets.service

import arrow.core.Either
import arrow.core.raise.effect
import arrow.core.raise.either
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.edu.agh.assets.dao.MapAssetDao
import pl.edu.agh.assets.dao.SavedAssetsDao
import pl.edu.agh.assets.domain.*
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.Utils
import java.io.File
import java.util.*

typealias IO<T> = Either<Throwable, T>

class SavedAssetsService(private val savedAssetsConfig: SavedAssetsConfig, private val defaultAssets: GameAssets) {
    private val logger by LoggerDelegate()

    fun getDefaultAssets(): GameAssets = defaultAssets

    suspend fun saveBasicAsset(
        name: String,
        loginUserId: LoginUserId,
        fileBody: ByteArray,
        fileType: FileType
    ): IO<SavedAssetsId> = saveNewFile(name, loginUserId, fileType, fileBody)

    suspend fun saveMap(
        name: String,
        loginUserId: LoginUserId,
        fileBody: ByteArray,
        mapAdditionalData: MapAssetDataDto
    ): IO<SavedAssetsId> =
        either {
            val id = saveNewFile(name, loginUserId, FileType.MAP, fileBody).bind()
            Transactor.dbQuery { saveMapAdditionalData(id, mapAdditionalData) }.bind()

            id
        }

    private fun saveMapAdditionalData(id: SavedAssetsId, mapAdditionalData: MapAssetDataDto): IO<Unit> =
        Either.catch {
            MapAssetDao.saveMapAdditionalData(id, mapAdditionalData)
        }

    private suspend fun saveNewFile(
        name: String,
        createdBy: LoginUserId,
        fileType: FileType,
        fileBody: ByteArray
    ): IO<SavedAssetsId> {
        val action = effect {
            val path = UUID.randomUUID().toString()

            val id = Transactor.dbQuery {
                SavedAssetsDao
                    .findByName(path)
                    .map { Exception("File already exists in database") }
                    .toEither { }
                    .swap()
                    .map {
                        SavedAssetsDao.insertNewAsset(name, createdBy, fileType, path)
                    }
            }.bind()

            logger.info("Saved file to db $name by $createdBy")

            val fullPath = savedAssetsConfig.getFullPath(path, fileType)
            val file = File(fullPath)

            val fileCreated = if (withContext(Dispatchers.IO) { file.createNewFile() }) {
                Either.Right(fullPath)
            } else {
                Either.Left(Exception("File already exists in file system"))
            }
            fileCreated.bind()

            Utils.catchPrint(logger) {
                file.writeBytes(fileBody)
            }

            logger.info("Saved file to disk $fullPath by $createdBy")

            id
        }

        return Utils.repeatUntilFulfilled(retrySaveUniqueName, action)
    }

    suspend fun getAllAssets(loginUserId: LoginUserId, fileType: FileType): List<SavedAssetDto> =
        Transactor.dbQuery { SavedAssetsDao.getAllAssets(loginUserId, fileType) }

    suspend fun getPath(savedAssetsId: SavedAssetsId): Either<Pair<HttpStatusCode, String>, String> =
        Transactor.dbQuery {
            Either.catch {
                SavedAssetsDao.getAssetById(savedAssetsId)
            }.map { (name, fileType) ->
                savedAssetsConfig.getFullPath(name, fileType)
            }.mapLeft {
                logger.error("Couldn't retrieve file from db $savedAssetsId", it)
                HttpStatusCode.NotFound to "Resource not found"
            }
        }

    suspend fun findMapConfig(savedAssetsId: SavedAssetsId): Either<Pair<HttpStatusCode, String>, MapAssetDataDto> =
        Transactor.dbQuery {
            MapAssetDao.findMapConfig(savedAssetsId)
        }.toEither { HttpStatusCode.NotFound to "Map asset not found" }

    companion object {
        private const val retrySaveUniqueName = 4
    }
}
