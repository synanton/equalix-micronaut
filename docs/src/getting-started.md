# Getting started

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (for the container image and local PostgreSQL)

## Build

```bash
mvn package
```

Produces `target/equalix-micronaut-1.0.0-SNAPSHOT.jar`.

## Run

The datasource URL is required (no default — see the configuration page for why):

```bash
EQUALIX_JDBC_URL=jdbc:postgresql://localhost:5432/equalix \
EQUALIX_DB_USER=equalix \
EQUALIX_DB_PASSWORD=equalix \
EQUALIX_API_KEY=changeme \
java -jar target/equalix-micronaut-1.0.0-SNAPSHOT.jar
```

Flyway migrations run at boot. The service listens on port 8080.

## Docker

```bash
docker build -t equalix-micronaut:local .
docker run --rm \
  -e EQUALIX_JDBC_URL=jdbc:postgresql://host:5432/equalix \
  -e EQUALIX_DB_USER=equalix \
  -e EQUALIX_DB_PASSWORD=equalix \
  -p 8080:8080 \
  equalix-micronaut:local
```

Or use the compose stack (Postgres + app, host ports configurable):

```bash
EQUALIX_API_KEY=secret docker compose up --build
```

## Verify

```bash
curl http://localhost:8080/health
# {"status":"UP"}

curl -X POST http://localhost:8080/api/v1/tasks \
  -H "X-API-Key: changeme" -H "Content-Type: application/json" \
  -d '{"fairnessKey":"acme","weight":1.0,"payload":"aGVsbG8="}'
# "8cf1b3f8-9ff1-41da-8d76-4231c1a4e110"
```

## Next steps

- [Concepts](concepts.md) — how the scheduler works
- [REST API](api.md) — the full endpoint reference
- [Configuration](configuration.md) — every config key
