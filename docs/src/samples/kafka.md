# Kafka ingest

Tasks can arrive via Kafka instead of REST. The consumer reads the ingestion topic
and creates one task per message (weight 1.0, non-sequential).

## Setup

```bash
docker compose up -d   # includes Kafka if present in the stack
```

Or point at an existing broker:

```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
EQUALIX_JDBC_URL=jdbc:postgresql://localhost:5432/equalix \
EQUALIX_DB_USER=equalix EQUALIX_DB_PASSWORD=equalix \
EQUALIX_API_KEY=changeme \
java -jar target/equalix-micronaut-1.0.0-SNAPSHOT.jar
```

## Produce

```bash
kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic equalix-tasks --property "parse.key=true" --property "key.separator=:"
```

```
acme:hello-world-payload
```

The message key becomes the fairness key (default `default`); the value is the
raw payload bytes.

## Semantics

- At-least-once: offsets commit after successful processing; a thrown exception
  leaves the offset uncommitted for redelivery.
- Empty payloads are acknowledged and dropped.
- Weight, sequencing, and dependencies are not expressible via Kafka — use REST
  for those.
