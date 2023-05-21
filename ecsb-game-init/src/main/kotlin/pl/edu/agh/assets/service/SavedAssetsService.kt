package pl.edu.agh.assets.service

import arrow.core.Either
import arrow.core.raise.effect
import arrow.core.raise.either
import io.ktor.http.*
import org.jetbrains.exposed.sql.insert
import pl.edu.agh.assets.dao.SavedAssetsDao
import pl.edu.agh.assets.domain.*
import pl.edu.agh.assets.table.MapAssetTable
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.Utils
import java.io.File
import java.util.*

typealias IO<T> = Either<Throwable, T>

class SavedAssetsService(private val savedAssetsConfig: SavedAssetsConfig) {
    private val logger by LoggerDelegate()

    suspend fun saveImage(name: String, loginUserId: LoginUserId, fileBody: ByteArray): IO<SavedAssetsId> =
        Transactor.dbQuery {
            saveNewFile(name, loginUserId, FileType.PNG, fileBody)
        }

    suspend fun saveMap(
        name: String,
        loginUserId: LoginUserId,
        fileBody: ByteArray,
        mapAdditionalData: MapAdditionalData
    ): IO<SavedAssetsId> =
        Transactor.dbQuery {
            either {
                val id = saveNewFile(name, loginUserId, FileType.MAP, fileBody).bind()
                saveMapAdditionalData(id, mapAdditionalData).bind()

                id
            }
        }

    private fun saveMapAdditionalData(id: SavedAssetsId, mapAdditionalData: MapAdditionalData): IO<Unit> =
        Either.catch {
            MapAssetTable.insert {
                it[this.id] = id
                it[this.startingX] = mapAdditionalData.startingPosition.x
                it[this.startingY] = mapAdditionalData.startingPosition.y
                it[this.characterSpreadsheetId] = mapAdditionalData.characterAssetsId
                it[this.tilesSpreadsheetId] = mapAdditionalData.assetId
            }
        }

    private suspend fun saveNewFile(
        name: String,
        createdBy: LoginUserId,
        fileType: FileType,
        fileBody: ByteArray
    ): IO<SavedAssetsId> {
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

        return Utils.repeatUntilFulfilled(retrySaveUniqueName, action)
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

    suspend fun findMapConfig(savedAssetsId: SavedAssetsId): Either<Pair<HttpStatusCode, String>, MapAssetDto> =
        Transactor.dbQuery {
            SavedAssetsDao.findMapConfig(savedAssetsId)
        }.toEither { HttpStatusCode.NotFound to "Map asset not found" }

    companion object {
        private const val retrySaveUniqueName = 4
    }
}
