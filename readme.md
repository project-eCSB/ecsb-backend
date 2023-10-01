# ecsb-backend

## Requirements

- Java 11
- Docker

## How to start services

In Docker, we have a couple of containers:

- `inzdb` - main postgres database
- `rabbitmq` - message broker for communication between services and analytical module
- `cache` - redis instance for caching players status in various interactions

To start all containers run:

- `docker-compose up`

## Application architecture
App consists of several modules that use a database, redis and communicate with each other through RabbitMQ.

### ecsb-game-init

Used for creating games as admin and boarding players into the game.

- `./gradlew ecsb-game-init:run`

### escb-moving

Used for moving players on map.

- `./gradlew ecsb-moving:run`

### ecsb-chat 

Used for :
- communication between players in game for such interactions as production or traveling
- input module for trade & coop messages sent between players (redirected to ecsb-game-engine)
- interaction propagator for production and traveling

Needs to be started with `rabbitmq`.

- `./gradlew ecsb-chat:run`

### ecsb-game-engine

Used for handling trade and coop players' states, validating according messages and sending
trade & coop response or their interaction propagation.

Needs to be started with `rabbitmq` and ecsb-chat (for incoming messages).

- `./gradlew ecsb-game-engine:run`

### ecbs-timer

Used for:
- providing new connected player with its game session time and his time tokens
- refreshing time tokens regeneration intervals
- notifying players that game session has ended or how much time is remaining

Needs to be started with `rabbitmq` and ecsb-chat (for incoming messages).

- `./gradlew ecsb-timer:run`

### ecsb-anal

Analytics module for logging all important stuff, that our client wants. 

### ecsb-backend-main

Common module for all others, contains such things as external services configuration (Postgres, Redis, RabbitMQ),
game-specific classes (session, player, db tables), authentication and utils.

### ecbs-test-clients

Module for backend services integrated testing.
