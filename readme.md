# ecsb-backend

## Requirements

- Java 11
- Docker

## How to start services

In Docker, we have a couple of containers:

- `inzdb` - main postgres database
- `rabbitmq` - message broker for communication between services
- `cache` - redis instance for caching players stats

To start all containers run:

- `docker-compose up`

### escb-moving module

Used for moving players on map.

Needs to be started with rabbitmq

- `./gradlew ecsb-moving:run`
