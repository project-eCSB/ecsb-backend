package pl.edu.agh.auth.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.utils.GenericIntId
import pl.edu.agh.utils.GenericIntIdFactory
import pl.edu.agh.utils.GenericIntIdSerializer
import pl.edu.agh.utils.genericIntId

@Serializable(with = LoginUserIdSerializer::class)
data class LoginUserId(override val id: Int) : GenericIntId<LoginUserId>()

private object LoginUserIdFactory : GenericIntIdFactory<LoginUserId>() {
    override fun create(id: Int): LoginUserId = LoginUserId(id)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LoginUserId::class)
object LoginUserIdSerializer : GenericIntIdSerializer<LoginUserId>(LoginUserIdFactory) {
    override fun deserialize(decoder: Decoder): LoginUserId = super.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: LoginUserId) = super.serialize(encoder, value)
}

fun Table.loginUserId(name: String): Column<LoginUserId> = genericIntId(LoginUserIdFactory)(name)
