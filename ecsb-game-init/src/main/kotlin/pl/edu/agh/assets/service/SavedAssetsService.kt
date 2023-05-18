package pl.edu.agh.assets.service

import arrow.core.Either
import arrow.core.raise.effect
import io.ktor.http.*
import pl.edu.agh.assets.dao.SavedAssetsDao
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetDto
import pl.edu.agh.assets.domain.SavedAssetsConfig
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.Utils
import java.io.File
import java.util.*

class SavedAssetsService(private val savedAssetsConfig: SavedAssetsConfig) {
    private val logger by LoggerDelegate()

    suspend fun saveNewFile(
        name: String,
        createdBy: LoginUserId,
        fileType: FileType,
        fileBody: ByteArray
    ): Either<Throwable, SavedAssetsId> {
        val action = effect {
            val path = UUID.randomUUID().toString()

            SavedAssetsDao.findByName(path).map { Exception("File already exists in database") }.toEither { }.swap()
                .bind()

            val fullPath = savedAssetsConfig.getFullPath(path, fileType)
            val file = File(fullPath)

            val fileCreated = if (file.createNewFile()) {
                Either.Right(fullPath)
            } else {
                Either.Left(Exception("File already exists in file system"))
            }
            fileCreated.bind()

            Utils.catchPrint(logger) {
                file.writeBytes(fileBody)
            }

            logger.info("Saved file to disk $fullPath by $createdBy")
            SavedAssetsDao.insertNewAsset(name, createdBy, fileType, path)
        }

        return Transactor.dbQuery { Utils.repeatUntilFulfilled(retrySaveUniqueName, action) }
    }

    suspend fun getAllMapAssets(loginUserId: LoginUserId, fileType: FileType): List<SavedAssetDto> =
        Transactor.dbQuery { SavedAssetsDao.getAllAssets(loginUserId, fileType) }

    suspend fun getPath(savedAssetsId: SavedAssetsId): Either<Pair<HttpStatusCode, String>, String> =
        Transactor.dbQuery {
            Either.catch { SavedAssetsDao.getAssetById(savedAssetsId) }.map { (name, fileType) ->
                savedAssetsConfig.getFullPath(name, fileType)
            }.mapLeft {
                logger.error("Couldn't retrieve file from db $savedAssetsId", it)
                HttpStatusCode.NotFound to "Resource not found"
            }
        }

    companion object {
        private const val retrySaveUniqueName = 4
    }
}
