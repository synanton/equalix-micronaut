# Testing

```bash
mvn test          # unit + integration (Testcontainers)
```

## Layout

- **Unit tests** — domain services, CMS adapters, executor, exception handlers.
  The 179 oracle unit tests that don't need Spring were ported verbatim; the 4
  Spring-coupled ones were rewritten against Micronaut idioms (in-JDK HTTP
  stub server, mocked Hibernate action queue, Lettuce mocks, direct handler
  tests).
- **Integration tests** — `integration/*IntegrationTest`, Micronaut Test +
  Testcontainers PostgreSQL, one shared container. Scheduling is disabled via
  test properties; jobs are driven explicitly so assertions stay deterministic.

## Coverage

235 tests green from clean at the family-matrix marker. The integration suite
covers: ingestion, virtual time, aging, sequential execution, CMS tx
consistency, CMS warm-up, watchdog drift metrics, CMS error sampling,
hierarchical fairness, proportional fairness (EQX-1 invariant), and
result-passthrough recovery.

## What the tests don't prove

Runtime behavior. The characterization runs (startup, RSS, GC, ceiling) live in
the family evidence — unit and integration tests prove semantics by
construction.
