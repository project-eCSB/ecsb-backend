val typesafeConfigVersion: String by rootProject
val hopliteVersion: String by rootProject
val jupiterEngineVersion: String by rootProject
val arrowKtVersion: String by rootProject
val amqpClientVersion: String by rootProject
val postgresDriverVersion: String by rootProject
val hikaricpVersion: String by rootProject
val ktorVersion: String by rootProject
val kotlinVersion: String by rootProject
val logbackVersion: String by rootProject
val exposedVersion: String by rootProject
val postgresVersion: String by rootProject
val koinVersion: String by rootProject
val koinKtor: String by rootProject
val mockkVersion: String by rootProject
val coroutinesVersion: String by rootProject
val lettuceCore: String by rootProject
val lettuceMod: String by rootProject
val prometheusVersion: String by rootProject

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10" apply false
    id("io.ktor.plugin") version "2.2.3" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    id("io.sentry.jvm.gradle") version "3.14.0"
}

allprojects {
    group = "pl.edu.agh"
    version = "0.0.1"

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

}

detekt {
    toolVersion = "1.23.1"
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

subprojects {
    repositories {
        mavenCentral()
        jcenter()
    }
    apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.kotlin.plugin.serialization")
        plugin("io.ktor.plugin")
        plugin("io.gitlab.arturbosch.detekt")
    }

    if (name == "ecsb-backend-main") {
        tasks.named("shadowJar") {
            enabled = false
        }
    }

    tasks {
        named<Test>("test") {
            useJUnitPlatform()
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        implementation("com.github.Ricky12Awesome:json-schema-serialization:0.6.6")

        implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
        implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")


        //di
        implementation("io.insert-koin:koin-core:$koinVersion")
        implementation("io.insert-koin:koin-ktor:$koinKtor")
        implementation("io.insert-koin:koin-logger-slf4j:$koinKtor")
        testImplementation("io.insert-koin:koin-test-junit5:$koinVersion")

        //slf4j
        implementation("ch.qos.logback:logback-classic:$logbackVersion")

        val detektPlugins by configurations
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.1")
        detektPlugins("com.wolt.arrow.detekt:rules:0.3.0")

        //tests
        testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
        testImplementation("org.junit.jupiter:junit-jupiter:$jupiterEngineVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterEngineVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiterEngineVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:$jupiterEngineVersion")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

        //fp
        implementation("io.arrow-kt:arrow-core:$arrowKtVersion")
        implementation("io.arrow-kt:arrow-fx-coroutines:$arrowKtVersion")

        //ktor
        implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
        implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
        implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
        implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
        implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
        implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
        implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
        implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
        testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")


        //typesafe configs
        implementation("com.typesafe:config:$typesafeConfigVersion")
        implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
        implementation("com.sksamuel.hoplite:hoplite-hocon:$hopliteVersion")

        //rabbitmq
        implementation("com.rabbitmq:amqp-client:$amqpClientVersion")

        //redis
        implementation("io.lettuce:lettuce-core:${lettuceCore}")
        implementation("com.redis:lettucemod:${lettuceMod}")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${coroutinesVersion}")

        //db
        implementation("com.zaxxer:HikariCP:$hikaricpVersion")
        implementation("org.postgresql:postgresql:$postgresDriverVersion")
        implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
        implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

        //suspend app
        implementation("io.arrow-kt:suspendapp:0.4.0")
        implementation("io.arrow-kt:suspendapp-ktor:0.4.0")

        //mocking/stubbing
        testImplementation("io.mockk:mockk:${mockkVersion}")

        // https://mvnrepository.com/artifact/io.sentry/sentry-logback
        implementation("io.sentry:sentry-logback:6.33.0")
    }
}