package pl.edu.agh.utils

import kotlin.time.Duration

data class HttpConfig(val host: String, val port: Int, val preWait: Duration)
