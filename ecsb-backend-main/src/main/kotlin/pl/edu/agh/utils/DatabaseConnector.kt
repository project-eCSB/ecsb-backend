package pl.edu.agh.utils

import arrow.core.Either
import arrow.core.raise.Effect
import arrow.core.raise.effect
import arrow.core.raise.either
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.slf4j.LoggerFactory

object DatabaseConnector {

    fun initDB() {
        val configPath = System.getProperty("hikariconfig", "/dbresource.properties")
        val dbConfig = HikariConfig(configPath)
        val dataSource = HikariDataSource(dbConfig)
        Database.connect(dataSource)
        LoggerFactory.getLogger(Application::class.simpleName).info("Initialized Database")
    }
}

object Transactor {
    private val logger by LoggerDelegate()

    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block(this) }

    suspend fun <L, R> dbQueryEffect(empty: L, block: suspend Transaction.() -> Either<L, R>): Effect<L, R> = effect {
        newSuspendedTransaction(Dispatchers.IO) {
            val caughtEither = Either.catch { block(this) }.onLeft {
                logger.error("Rollback, unknown error (caught), ${it.message}", it)
                rollback()
            }.mapLeft { empty }.onRight {
                it.onLeft {
                    logger.error("Rollback, user error $it")
                    rollback()
                }
            }
            either {
                caughtEither.bind().bind()
            }
        }.bind()
    }

    suspend fun <T> transactional(block: suspend Transaction.() -> T): Deferred<T> {
        return suspendedTransactionAsync(Dispatchers.IO) {
            block(this)
        }
    }
}
