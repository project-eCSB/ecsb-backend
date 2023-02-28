package pl.edu.agh.plugins

import arrow.core.Either
import arrow.core.continuations.Effect
import arrow.core.continuations.effect
import arrow.core.continuations.either
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
import pl.edu.agh.simple.SimpleTable

object DatabaseConnector {


    fun Application.initDB() {
        val configPath = "/dbresource.properties"
        val dbConfig = HikariConfig(configPath)
        val dataSource = HikariDataSource(dbConfig)
        Database.connect(dataSource)
        SimpleTable.create()
        LoggerFactory.getLogger(Application::class.simpleName).info("Initialized Database")
    }

}

object Transactor {
    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block(this) }

    suspend fun <L, R> dbQueryEffect(block: suspend Transaction.() -> Either<L, R>, empty: L): Effect<L, R> =
        effect {
            newSuspendedTransaction(Dispatchers.IO) {
                val caughtEither =
                    Either
                        .catch { block(this) }
                        .tapLeft {
                            println("Rollback, unknown error (caught), ${it.message}")
                            rollback()
                        }
                        .mapLeft { empty }
                        .tap {
                            it.tapLeft {
                                println("Rollback, user error $it")
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
