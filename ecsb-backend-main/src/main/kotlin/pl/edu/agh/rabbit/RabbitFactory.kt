package pl.edu.agh.rabbit

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.release
import arrow.fx.coroutines.resource
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory

object RabbitFactory {
    private lateinit var rabbitConfig: RabbitConfig

    fun initialize(rabbitConfig: RabbitConfig) {
        this.rabbitConfig = rabbitConfig
    }

    private val rabbitConnection: Lazy<Connection> = lazy {
        createConnection()
    }

    private fun createConnection(): Connection {
        val factory = ConnectionFactory().apply {
            isAutomaticRecoveryEnabled = true
            host = rabbitConfig.host
            port = rabbitConfig.port
            password = rabbitConfig.password.value
            username = rabbitConfig.username
            virtualHost = rabbitConfig.vhost
        }
        val connection = factory.newConnection()

        Runtime.getRuntime().addShutdownHook(Thread {
            if (connection.isOpen) {
                connection.close()
            }
        })

        return connection
    }

    fun getChannelResource(): Resource<Channel> = resource {
        val connection = rabbitConnection.value
        connection.createChannel()
    } release { it.close() }

    fun getChannelResource(queueName: String): Resource<Channel> = resource {
        val connection = rabbitConnection.value
        connection.createChannel()
    } release {
        it.queuePurge(queueName)
        it.close()
    }

}
