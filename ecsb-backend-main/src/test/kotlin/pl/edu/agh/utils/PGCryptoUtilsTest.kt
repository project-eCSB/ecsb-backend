package pl.edu.agh.utils

import org.jetbrains.exposed.sql.QueryBuilder
import org.junit.jupiter.api.Test
import pl.edu.agh.auth.domain.Password
import pl.edu.agh.utils.PGCryptoUtils.toDbValue
import kotlin.test.assertEquals

class PGCryptoUtilsTest {

    @Test
    fun selectEncryptedPassword() {
        val cryptExpr = PGCryptoUtils.CryptExpression("kolumna", Password("tajne"))

        val queryBuilder = QueryBuilder(false)
        cryptExpr.toQueryBuilder(queryBuilder)

        assertEquals("kolumna = crypt('tajne', kolumna)", queryBuilder.toString())
    }

    @Test
    fun toDbValue() {
        val queryBuilder = QueryBuilder(false)
        Password("tajne").toDbValue().toQueryBuilder(queryBuilder)

        assertEquals("crypt('tajne', gen_salt('bf'))", queryBuilder.toString())
    }
}
