val typesafeConfigVersion: String by rootProject
val hopliteVersion: String by rootProject
val jupiterEngineVersion: String by rootProject
val arrowKtVersion: String by rootProject
val amqpClientVersion: String by rootProject
val kredsVersion: String by rootProject
val postgresDriverVersion: String by rootProject
val hikaricpVersion: String by rootProject
val ktorVersion: String by rootProject
val kotlinVersion: String by rootProject
val logbackVersion: String by rootProject
val exposedVersion: String by rootProject
val postgresVersion: String by rootProject
val koinVersion: String by rootProject
val koinKtor: String by rootProject

plugins {
    kotlin("jvm") version "1.8.10" apply false
    id("io.ktor.plugin") version "2.2.3" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10" apply false
}

allprojects {
    group = "pl.edu.agh"
    version = "0.0.1"

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

}


subprojects {
    repositories {
        mavenCentral()
    }
    apply {
        plugin("io.ktor.plugin")
        plugin("org.jetbrains.kotlin.plugin.serialization")
    }

    tasks {
        named<Test>("test") {
            useJUnitPlatform()
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        //di
        implementation("io.insert-koin:koin-core:$koinVersion")
        implementation("io.insert-koin:koin-ktor:$koinKtor")
        implementation("io.insert-koin:koin-logger-slf4j:$koinKtor")
        testImplementation("io.insert-koin:koin-test-junit5:$koinVersion")

        //slf4j
        implementation("ch.qos.logback:logback-classic:$logbackVersion")


        //tests
        testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
        testImplementation("org.junit.jupiter:junit-jupiter:$jupiterEngineVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterEngineVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiterEngineVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:$jupiterEngineVersion")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

        //fp
        implementation("io.arrow-kt:arrow-core:$arrowKtVersion")

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
        implementation("io.github.crackthecodeabhi:kreds:$kredsVersion")

        //db
        implementation("com.zaxxer:HikariCP:$hikaricpVersion")
        implementation("org.postgresql:postgresql:$postgresDriverVersion")
        implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
        implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    }
}