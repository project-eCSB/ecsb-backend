package pl.edu.agh.rabbit

import com.rabbitmq.client.ConnectionFactory

object RabbitFactory {
    fun getConnectionFactory(rabbitConfig: RabbitConfig): ConnectionFactory =
        ConnectionFactory().run {
            isAutomaticRecoveryEnabled = true
            host = rabbitConfig.host
            port = rabbitConfig.port
            password = rabbitConfig.password.value
            username = rabbitConfig.username

            this
        }
}
