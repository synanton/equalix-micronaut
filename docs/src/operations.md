# Operations

## Health and readiness

```
GET /health    → {"status":"UP"}
GET /info
```

Both unauthenticated. Container probes target `/health`.

## Metrics

```
GET /prometheus
```

Micrometer/Prometheus exposition with the same metric names as the oracle:

- `equalix.adaptive.rps` — current RPS
- `equalix.task.duration_seconds{success}` — completion durations
- `equalix.task.errors_total` — failure counter
- `equalix.cms.estimation.error_count{direction}` / `..._magnitude` — estimation error sampling (opt-in)
- `equalix.cms.estimation.drift{fairnessKey,layer}` plus `_max`, `_min`, `_absolute`, `_keys`, `_keys.sampled`, `_timestamp_seconds` — watchdog drift
- `equalix.hierarchy.dispatches_total{layer,node}` — hierarchical dispatch counters

## Logs

The startup milestones give the internal phase breakdown:

```
startup milestone phase=main-entry
startup milestone phase=context-ready
```

## Container

```bash
docker build -t equalix-micronaut:local .
```

Runtime image: `eclipse-temurin:21-jre` + the application jar. Image size
characterized at 386 MB — see the family comparison.

## Database

PostgreSQL 16, Flyway-managed schema (six migrations, byte-identical to the
oracle's). The `shedlock` table exists but is unused — distributed job locking is
named out for this implementation (single-instance comparison scope).
