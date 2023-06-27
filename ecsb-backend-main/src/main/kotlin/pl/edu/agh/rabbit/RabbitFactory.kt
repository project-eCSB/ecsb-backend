package pl.edu.agh.rabbit

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory

object RabbitFactory {
    private fun getConnectionFactory(rabbitConfig: RabbitConfig): ConnectionFactory =
        ConnectionFactory().run {
            isAutomaticRecoveryEnabled = true
            host = rabbitConfig.host
            port = rabbitConfig.port
            password = rabbitConfig.password.value
            username = rabbitConfig.username

            this
        }

    fun getChannelResource(rabbitConfig: RabbitConfig): Resource<Channel> = resource(
        acquire = {
            val factory = getConnectionFactory(rabbitConfig)
            val connection = factory.newConnection()
            val channel = connection.createChannel()

            connection to channel
        },
        release = { resources, _ ->
            val (connection, channel) = resources
            channel.close()
            connection.close()
        }
    ).map { it.second }
}
