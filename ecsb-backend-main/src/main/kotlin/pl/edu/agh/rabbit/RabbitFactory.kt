package pl.edu.agh.rabbit

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.release
import arrow.fx.coroutines.resource
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory

object RabbitFactory {

    fun getChannelResource(connection: Connection): Resource<Channel> = resource {
        connection.createChannel()
    } release { it.close() }

    fun getConnection(rabbitConfig: RabbitConfig): Resource<Connection> = resource {
        ConnectionFactory().apply {
            isAutomaticRecoveryEnabled = true
            host = rabbitConfig.host
            port = rabbitConfig.port
            password = rabbitConfig.password.value
            username = rabbitConfig.username
            virtualHost = rabbitConfig.vhost
        }.newConnection()
    } release {
        it.close()
    }
}
