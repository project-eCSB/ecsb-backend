package pl.edu.agh.analytics

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import pl.edu.agh.analytics.service.AnalyticsServiceImpl
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector

fun main(): Unit = SuspendApp {
    val analyticsConfig = ConfigUtils.getConfigOrThrow<AnalyticsConfig>()

    resourceScope {
        DatabaseConnector.initDBAsResource().bind()
        val connection = RabbitFactory.getConnection(analyticsConfig.rabbitConfig).bind()

        InteractionConsumerFactory.create(
            AnalyticsConsumer(AnalyticsServiceImpl()),
            "",
            connection
        ).bind()

        awaitCancellation()
    }
}
