# Introduction

`equalix-micronaut` is the Micronaut port of the Equalix fair scheduler — the third
implementation in the family, after the Spring Boot oracle ([`equalix`](https://github.com/synanton/equalix))
and the Go reimplementation ([`equalix-go`](https://github.com/synanton/equalix-go)).

It runs the same scheduler the other two run:

- Virtual-time weighted-fair scheduling
- Count-Min Sketch in-flight estimation with watchdog reconciliation
- Adaptive RPS controller
- Hierarchical fairness, per-tenant quotas, anti-starvation
- Sequential execution mode

The scheduling semantics are pinned to the oracle by construction — same schema, same
REST contract, same executor protocol. The port exists to characterize the runtime
variable, not to change the scheduler.

## Why Micronaut

`equalix-go` compares two things at once against the Spring Boot oracle: runtime
(compiled Go vs. JVM) and framework (none vs. Spring Boot). When it reports a
cold-start advantage, it is not obvious which factor dominates.

`equalix-micronaut` adds a third column that separates them:

| | Runtime | DI / context | Reflection at runtime |
|---|---|---|---|
| **equalix** (Spring Boot) | JVM | Spring context, classpath scan | Yes, heavy |
| **equalix-micronaut** | JVM | Micronaut AOT, compile-time DI | No |
| **equalix-go** | Native binary | None | No |

The question this project answers: how much of Spring Boot's cold-start and memory
profile is the JVM itself, and how much is Spring's runtime machinery?

## Status

**Complete.** Ported, characterized on four dimensions (startup, warm RSS,
GC/latency, RPS ceiling), and differentially validated against the oracle at one
provenance. See the [family comparison](family-comparison.md) for the measured
numbers and the stopping-point declaration.

## Documentation map

- **Using** — getting started, concepts, lifecycle, REST API, configuration, operations
- **Samples** — runnable walkthroughs (REST, executor, sequential, Kafka)
- **Developing** — codebase map, hexagonal architecture, testing, contributing
- **Family** — the three-implementation comparison this port participates in
