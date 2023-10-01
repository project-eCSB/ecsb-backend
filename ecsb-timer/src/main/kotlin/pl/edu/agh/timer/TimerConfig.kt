package pl.edu.agh.timer

import pl.edu.agh.rabbit.RabbitConfig

@JvmInline
value class TimerConfig(val rabbit: RabbitConfig)
