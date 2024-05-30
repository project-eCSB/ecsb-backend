package pl.edu.agh.analytics

data class DecisionModelConfig(
    val enable: Boolean,
    val host: String,
    val port: Int,
    val postData: String
)