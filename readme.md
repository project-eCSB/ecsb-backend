# ecsb-backend

## Requirements

- Java 11
- Docker

## How to start services

In Docker, we have a couple of containers:

- `inzdb` - main postgres database
- `rabbitmq` - message broker for communication between services and analitycal module
- `cache` - redis instance for caching players stats

To start all containers run:

- `docker-compose up`

### escb-moving module

Used for moving players on map.

Needs to be started with `cache` containter

- `./gradlew ecsb-moving:run`

### ecsb-chat module

Used for communication between players in game

Needs to be started with cache and ecsb-moving (for multicast)

- `./gradlew ecsb-chat:run`
