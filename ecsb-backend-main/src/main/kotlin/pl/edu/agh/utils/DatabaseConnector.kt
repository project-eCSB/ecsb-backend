package pl.edu.agh.utils

import arrow.core.Either
import arrow.core.raise.Effect
import arrow.core.raise.effect
import arrow.core.raise.either
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.slf4j.LoggerFactory

object DatabaseConnector {

    fun initDBAsResource(): Resource<Unit> = resource(
        acquire = {
            val configPath = System.getProperty("hikariconfig", "/dbresource.properties")
            val dbConfig = HikariConfig(configPath)
            val dataSource = HikariDataSource(dbConfig)
            val database = Database.connect(dataSource)
            LoggerFactory.getLogger(Application::class.simpleName).info("Initialized database")

            database
        },
        release = { database, _ ->
            TransactionManager.closeAndUnregister(database)
            LoggerFactory.getLogger(Application::class.simpleName).info("Uninitialized database")
        }
    ).map {}
}

object Transactor {
    private val logger by LoggerDelegate()

    suspend fun <T> dbQuery(block: Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block(this) }

    suspend fun <L, R> dbQueryEffect(empty: L, block: Transaction.() -> Either<L, R>): Effect<L, R> = effect {
        newSuspendedTransaction(Dispatchers.IO) {
            val caughtEither = Either.catch { block(this) }.onLeft {
                logger.error("Rollback, unknown error (caught), ${it.message}", it)
                rollback()
            }.mapLeft { empty }.onRight {
                @Suppress("detekt:NoEffectScopeBindableValueAsStatement")
                it.onLeft { error ->
                    logger.error("Rollback, user error $error")
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

object PGCryptoUtils {

    class CryptExpression(private val columnName: String, expr: Sensitive) : Op<Boolean>() {
        private val parsedExpr = VarCharColumnType().notNullValueToDB(expr.value)

        override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit =
            queryBuilder { append("$columnName = crypt('$parsedExpr', $columnName)") }
    }

    fun Column<String>.selectEncryptedPassword(expr: Sensitive): Op<Boolean> {
        return CryptExpression(this.name, expr)
    }

    fun Sensitive.toDbValue(): Expression<String> = Expression.build {
        PGCryptFunction(this@toDbValue)
    }

    class PGCryptFunction(val expr: Sensitive) : Expression<String>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            append("crypt", '(')
            registerArgument(VarCharColumnType(), expr.value)
            append(", gen_salt('bf'))")
        }
    }
}
