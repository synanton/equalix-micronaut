# Configuration

All configuration is via `application.yml` (or environment overrides using the
standard `${ENV_VAR}` placeholder syntax). Scheduler jobs are enabled by default;
set `app.scheduling.enabled=false` to drive the pipeline manually (the test suite
does this).

## Datasource (required)

| Key | Notes |
|---|---|
| `EQUALIX_JDBC_URL` | **Required, no default.** Micronaut's placeholder parser silently mangles defaults containing `://` — a URL default would resolve to garbage. Fail fast instead. |
| `EQUALIX_DB_USER` | Database user |
| `EQUALIX_DB_PASSWORD` | Database password |

Flyway runs the migration set at boot (`docs/` mirrors the oracle's V1–V6;
the `shedlock` table is applied but unused — see
[family comparison](family-comparison.md)).

## Security

| Key | Default | Notes |
|---|---|---|
| `app.security.api-key` | `changeme` | Required header `X-API-Key` on every endpoint except `/health` and `/info` |

## Executor

| Key | Default | Notes |
|---|---|---|
| `app.executor.base-url` | `http://localhost:9090` | Where tasks are POSTed |
| `app.executor.connect-timeout-ms` | 2000 | |
| `app.executor.read-timeout-ms` | 5000 | |

## Scheduling

| Key | Default | Notes |
|---|---|---|
| `app.scheduling.enabled` | `true` | Master switch for all jobs |
| `app.queue.dispatcher-interval` | 50 (ms) | Flat dispatcher tick |
| `app.queue.priority-calc-interval` | 100 (ms) | Priority calculator tick |
| `app.queue.sequential.dispatcher-interval` | 50 (ms) | Sequential dispatcher tick |
| `app.queue.sequential.block-recovery-interval` | 10000 (ms) | Blocked-client recovery |
| `app.queue.sequential.result-passthrough-interval` | 60000 (ms) | Dependency result pass-through |
| `app.watchdog.interval-minutes` | 5 | Watchdog reconcile interval |

## Queue

| Key | Default | Notes |
|---|---|---|
| `app.queue.max-tasks-in-process` | 5000 | Global in-flight cap |
| `app.queue.max-per-client-quota` | 500 | Per-key cap |
| `app.queue.worker-poll-size` | 100 | Rows locked per tick |
| `app.queue.max-queued-time-ms` | 60000 | Starvation promotion deadline |
| `app.queue.task-timeout-ms` | 300000 | In-flight timeout |
| `app.queue.max-payload-bytes` | 1048576 | Payload size limit |
| `app.queue.fairness-mode` | `flat` | `flat` or `hierarchical` |

### Virtual time

| Key | Default | Notes |
|---|---|---|
| `app.queue.virtual-time.quantum` | 1000 | Virtual-time units per weight-1 task |

### Aging

| Key | Default | Notes |
|---|---|---|
| `app.queue.aging.policy` | `none` | `none`, `linear`, `log`, `power` |
| `app.queue.aging.lambda` | 1000 | Aging rate |
| `app.queue.aging.gamma` | 2.0 | Exponent for `power` |
| `app.queue.aging.candidate-pool-size` | 200 | Rows locked per ordering |

### CMS

| Key | Default | Notes |
|---|---|---|
| `app.queue.cms.mode` | `local` | `local` (in-memory) or `redis` (shared) |
| `app.queue.cms.width` | 65536 | Columns |
| `app.queue.cms.depth` | 5 | Hash rows |
| `app.queue.cms.redis.key-namespace` | `equalix:cms` | Redis key prefix |
| `app.queue.cms.redis.fallback-to-local` | `true` | Fall back on Redis failure |
| `app.queue.cms.error-sampling.enabled` | `false` | Estimation-error sampler |
| `app.queue.cms.error-sampling.interval-ms` | 1000 | Sampler interval |

### Hierarchical

| Key | Default | Notes |
|---|---|---|
| `app.hierarchical.separator` | `/` | Key segment separator |
| `app.hierarchical.layers` | organization, department | Layer definitions with default weights |
| `app.hierarchical.weights` | `{}` | Per-node overrides |
| `app.hierarchical.metrics-depth` | 1 | Layers getting dispatch counters |

## Adaptive RPS

| Key | Default | Notes |
|---|---|---|
| `app.adaptive-rps.enabled` | `true` | |
| `app.adaptive-rps.initial-rps` | 1 | |
| `app.adaptive-rps.min-rps` | 1 | |
| `app.adaptive-rps.max-rps` | 100 | |
| `app.adaptive-rps.target-latency-ms` | 200 | |
| `app.adaptive-rps.latency-threshold` | 0.2 | Dead-band fraction |
| `app.adaptive-rps.error-threshold` | 0.05 | Emergency-brake threshold |
| `app.adaptive-rps.window-size` | 100 | Sliding window |
| `app.adaptive-rps.min-samples` | 10 | |
| `app.adaptive-rps.emergency-factor` | 0.5 | |
| `app.adaptive-rps.decrease-factor` | 0.9 | |
| `app.adaptive-rps.increase-factor` | 1.05 | |
| `app.adaptive-rps.increase-error-threshold` | 0.01 | |
| `app.adaptive-rps.adjustment-interval-ms` | 2000 | Min time between adjustments |
| `app.adaptive-rps.latency-ema-alpha` | 0.7 | EMA weight |
| `app.adaptive-rps.direction-change-confirmations` | 3 | Direction reversal dampener |

## Kafka

| Key | Default | Notes |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `app.kafka.topics.ingestion` | `equalix-tasks` | Consumer topic |
| `kafka.consumers.default.group-id` | `equalix-ingestion` | |

The consumer commits offsets after successful processing (at-least-once); empty
payloads are dropped. Without a broker the consumer retries in the background —
`/health` still reports UP (the Kafka health indicator is disabled by design).
