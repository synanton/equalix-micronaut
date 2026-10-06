# equalix-micronaut

> A third implementation of the Equalix scheduler — Java + Micronaut — built to characterize JVM startup, image size, and runtime footprint alongside the Spring Boot oracle and the Go reimplementation.

[![Java Version](https://img.shields.io/badge/java-21-ED8B00?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Micronaut](https://img.shields.io/badge/micronaut-4.x-1B6E8C)](https://micronaut.io)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-pre--alpha-orange.svg)](#status)

`equalix-micronaut` is a spec-first reimplementation of [Equalix](https://github.com/synanton/equalix) in Java + [Micronaut](https://micronaut.io), built as a **third comparison point** in the Equalix family. Its scheduling semantics are the same as the Spring Boot oracle by construction — same schema, same REST contract, same executor protocol. Its reason to exist is measurement, not novelty.

---

## Status

**Pre-alpha.** Scoped, not yet implemented. See [`docs/implementation.md`](docs/implementation.md) for the phased plan.

**State as of:** *(populated at first commit)* — scoped, no code.
**Oracle:** Java Spring Boot Equalix at the SHA recorded in [`docs/spec.md`](docs/spec.md) §13 (see the oracle determination entry).
**Family baseline:** [equalix](https://github.com/synanton/equalix) (Spring Boot), [equalix-go](https://github.com/synanton/equalix-go) (Go).

---

## What It Is

`equalix-micronaut` runs the same scheduler the other two implementations run:

- Virtual-time weighted-fair scheduling
- Count-Min Sketch in-flight estimation with watchdog reconciliation
- Adaptive RPS controller
- Hierarchical fairness, per-tenant quotas, anti-starvation
- Sequential execution mode

The scheduling semantics are not the point of this project — those are established by the oracle and validated by [`equalix-go`](https://github.com/synanton/equalix-go)'s differential harness. The point is to **isolate the runtime variable** from the framework variable.

---

## Why Micronaut

`equalix-go` compares two things at once when placed against the Spring Boot oracle:

- **Runtime**: compiled Go vs. JVM
- **Framework**: no framework vs. Spring Boot

Spring Boot's startup cost is a compound of JVM class loading, Spring's context scan and bean wiring, and reflection-based DI. When `equalix-go` reports "50× faster cold start," it's not obvious which of those factors dominates.

`equalix-micronaut` adds a third column that separates them:

|                           | Runtime       | DI / context                   | Reflection at runtime |
| ------------------------- | ------------- | ------------------------------ | --------------------- |
| **equalix** (Spring Boot) | JVM           | Spring context, classpath scan | Yes, heavy            |
| **equalix-micronaut**     | JVM           | Micronaut AOT, compile-time DI | No                    |
| **equalix-go**            | Native binary | None                           | No                    |

Three-way comparison, two variables isolated:

- **JVM vs. compiled** — Go vs. Micronaut
- **Reflection-heavy vs. AOT JVM** — Spring Boot vs. Micronaut
- **Framework-free vs. framework-managed** — Go vs. both JVMs

The interesting question this project answers: how much of Spring Boot's cold-start and memory profile is the JVM itself, and how much is Spring's runtime machinery?

---

## What It Is Not

- **Not a new scheduler.** Semantics are pinned to the oracle. Any divergence is a bug, not a design choice.
- **Not a fork of the Spring Boot project.** Separate code, separate repository, same behavioral contract.
- **Not a rewrite-for-rewrite's-sake.** If the answer were already known ("Micronaut starts faster than Spring Boot"), the project would have no reason to exist. The point is to measure it here, on this workload, with the same fairness invariant.
- **Not a candidate for the oracle.** The Spring Boot implementation remains the behavioral reference. Micronaut is a comparison point.

---

## Architecture

The same hexagonal split as the other two implementations, expressed in Micronaut idioms:

                        ┌────────────────────────────────────┐
                        │    cmd (Micronaut application)     │
                        └────────────────┬───────────────────┘
                                         │
                        ┌────────────────┼────────────────┐
                        │                │                │
                   ┌────▼─────┐    ┌─────▼──────┐  ┌──────▼─────┐
                   │ REST API │    │  Scheduled │  │ Metrics /  │
                   │ (HTTP)   │    │  jobs      │  │  Health    │
                   └────┬─────┘    └─────┬──────┘  └──────┬─────┘
                        │                │                │
                        └────────────────┼────────────────┘
                                         │
                                 ┌───────▼────────┐
                                 │  Domain core   │
                                 │  pure Java)    │
                                 └───────┬────────┘
                                         │
                        ┌────────────────┼────────────────┐
                        │                │                │
                 ┌──────▼───────┐  ┌─────▼──────┐    ┌────▼──────┐
                 │ PostgreSQL   │  │   Redis    │    │  Executor │
                 │ (JDBC)       │  │ (Lettuce)  │    │   (HTTP)  │
                 └──────────────┘  └────────────┘    └───────────┘


**Constraints inherited from the family:**

- **Same schema** — the migration set is the oracle's, byte-identical. Schema divergence would invalidate every comparison.
- **Same REST contract** — [`docs/api.md`](docs/api.md) from the oracle repo describes the endpoints; the Micronaut implementation serves the same paths, bodies, and status codes.
- **Same executor protocol** — `POST /tasks/{id}/execute` out, `POST /api/v1/tasks/{id}/complete` back. Same shape as the other two.
- **Same domain semantics** — the virtual-time formula, priority calculation, CMS behavior, adaptive RPS, watchdog, and timeout sweep follow the spec ([`docs/spec.md`](docs/spec.md) §13) without reinterpretation. Where Micronaut idioms differ from Spring idioms, the *behavior* is what's preserved.

---

## The Family

Three implementations of one scheduler, one oracle:
```text

          ┌──────────────────────┐
          │  Java Spring Boot    │
          │  equalix (oracle)    │
          └──────────┬───────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
 ┌──────▼─────┐ ┌────▼──────┐ ┌───▼─────────┐
 │ equalix-go │ │ equalix-  │ │  (future    │
 │ (compiled) │ │ micronaut │ │   impls)    │
 └────────────┘ └───────────┘ └─────────────┘

```
**Shared:**

- The oracle determination (which Java SHA the semantics are pinned to)
- The evidence taxonomy — DECISION / CORRECTION / NOTE, recorded in spec §13
- The differential harness pattern — same workloads, same stub executor protocol, same fairness gate
- The maturity-table shape — implemented / tested / conformance / benchmark / differential

**Per-implementation:**

- Language, framework, runtime
- Adapter layer (JDBC vs pgx; Micronaut HTTP client vs net/http)
- Which dimensions it's positioned to characterize (see below)

---

## Comparison Dimensions

`equalix-micronaut` exists to fill specific cells in the family's comparison matrix. The dimensions below are the interesting ones; scheduler-level throughput is deliberately not first.

### Startup profile

|  Phase                   | Spring Boot | Micronaut | Go |
|--------------------------|-------------|-----------|---|
| Spawn → `main()`         | | | |
| `main()` → context ready | | | |
| Context → `/readyz` 200  | | | |
| Ready → first dispatch   | | | |
| **Total: spawn → first dispatch** | | | |

Micronaut's AOT annotation processing should collapse the "context ready" phase; the JVM spawn cost is shared with Spring Boot.

### Container footprint

|                | Spring Boot | Micronaut |   Go   |
|----------------|-------------|-----------|--------|
| Image size     | | | |
| Cold RSS       | | | |
| Warm RSS       | | | |
| RSS under load | | | |

### Runtime characteristics

|                                    | Spring Boot | Micronaut | Go |
|------------------------------------|-------------|-----------|----|
| GC pauses (p99)                    | | | n/a |
| CPU per dispatch                   | | | |
| Sustained RPS at ceiling           | | | |
| Recovery time after backend outage | | | |

### Optional fourth column — GraalVM native-image

Micronaut supports GraalVM native-image compilation, which would produce a fourth runtime profile:

|              | Spring Boot (JVM) | Micronaut (JVM) | Micronaut (native) | Go |
|--------------|-------------------|-----------------|--------------------|----|
| Cold start   | | | | |
| Image size   | | | | |

**Deferred.** Native-image build complexity is non-trivial and would expand the scope. Mentioned here so the matrix has room; not committed to.

---

## Non-Goals

- **Scheduler feature additions** beyond oracle parity
- **New REST endpoints**, new config keys, new operational surfaces
- **Alternative persistence** — same schema, same DB
- **Different fairness semantics** — any divergence is a bug
- **Becoming the oracle** — Spring Boot remains the reference
- **Kubernetes operators, Helm charts, or deploy tooling** — the artifact is a container image, not a deployment
- **gRPC** — the executor protocol is HTTP, same as the other two

---

## Relationship to `equalix-go`

`equalix-go` and `equalix-micronaut` share:

- The oracle (Spring Boot Equalix at the pinned SHA)
- The evidence taxonomy
- The workload format and pace knob
- The stub executor protocol
- The differential methodology

They differ in:

- **What they isolate.** `equalix-go` isolates "compiled + no framework." `equalix-micronaut` isolates "JVM + AOT, no runtime reflection."
- **What they're expected to find.** `equalix-go` should show the compiled-runtime advantage in cold start and RSS. `equalix-micronaut` should show the AOT advantage *within* the JVM, and the residual JVM cost over Go.
- **What they can't answer alone.** Neither tells you whether Spring Boot's profile is a JVM fact or a Spring fact. Together they do.

---

## Maturity

| Area | Implemented | Tested | Conformance-validated | Benchmark-validated | Differentially validated vs. Spring Boot |
|---|---|---|---|---|---|
| Domain core | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| PostgreSQL adapter | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| HTTP surface | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| Jobs (dispatcher, calculator, watchdog, timeout) | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| Adaptive RPS | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| Container image | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |
| Startup profile | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |

Rows flip on the same evidence thresholds the family uses: **✅** only when the corresponding artifact exists in `docs/evidence/`, **⬜** otherwise. Nothing is claimed without a committed citation.

---

## Roadmap

See [`docs/implementation.md`](docs/implementation.md) for the phased plan. The family's phase numbering continues from `equalix-go`'s EQLX-0 through EQLX-7:

Phases are shared across the family — phase as stage, never as lockstep. The list below is the same stages, with this repo's per-phase scope differing: Phase 0 here is trivial (spec reuse, not re-extraction — one commit), Phase 1 domain core is a full rebuild (different language, same semantics), and Phase 5 differential is the first non-scaffolding phase (everything before it exists to enable the comparison). Do not read equal effort across repos from shared numbers.

- **Phase 0** — Specification extraction (reuse `equalix-go`'s spec; no re-extraction)
- **Phase 1** — Domain core (pure Java, no Micronaut dependencies)
- **Phase 2** — Adapters (JDBC, Lettuce, Micronaut HTTP client)
- **Phase 3** — Jobs (dispatcher, calculator, watchdog, timeout)
- **Phase 4** — Adaptive RPS
- **Phase 5** — Differential validation against the oracle
- **Phase 6** — Container image, health/readiness, metrics
- **Phase 7** — Comparison evidence — the four startup/footprint/runtime tables above, filled with measured numbers

The evidence phase is where this project's value lands. Everything before it is preparation.

---

## Quick Start

*(Placeholder — filled at Phase 2 completion.)*

```bash
git clone https://github.com/synanton/equalix-micronaut.git
cd equalix-micronaut
./mvnw package
docker build -t equalix-micronaut:local .
docker run --rm \
  -e EQUALIX_DSN=postgres://equalix:equalix@host:5432/equalix \
  -p 8080:8080 \
  equalix-micronaut:local
```



------

## Contributing

Same discipline as the rest of the family:

- Conventional commits with phase scope
- `EQLX-` branch prefix (shared across the family)
- Training gate (craft) and production gate (evidence) — see [`CONTRIBUTING.md`](https://contributing.md/)
- Freshness marker bumps on phase transitions only
- DECISION / CORRECTION / NOTE taxonomy for spec §13 entries

------

## License

Apache License 2.0. See [`LICENSE`](https://license/).

------

## Acknowledgments

Third implementation in the Equalix family. Oracle: [equalix](https://github.com/synanton/equalix) (Spring Boot). Sibling: [equalix-go](https://github.com/synanton/equalix-go).

---

Two things to flag before this goes up as written.

**The freshness marker is unfilled.** The block reads `*(populated at first commit)*` and needs an actual SHA + date before the README is honest. The pattern from the family is `State as of: <SHA> (date) — <phase state>` with the oracle SHA on a second line. Populate when the first commit lands — don't ship the marker with the placeholder in it, because the whole point of the marker is to be a specific, checkable reference.

**Phase numbering.** I wrote "continues from `equalix-go`'s EQLX-0 through EQLX-7" and then listed a fresh Phase 0 through 7 for this repo. Two options:

1. **Shared phase numbers across the family** — phase N means the same conceptual stage in every repo. EQLX-0 is spec extraction everywhere, even if Micronaut's EQLX-0 is a single commit that reads and reuses the existing spec. Cleaner conceptually; the branch prefix `EQLX-N-...` stays coherent across repos.
2. **Per-repo phase numbers** — each repo counts from its own zero. Simpler mechanically, but `EQLX-3` means different things in different repos, and status updates have to disambiguate.

I'd lean toward (1) — shared phase numbers. The family is one conceptual project with three implementations; the phase labels should be too. But it depends on whether you want to keep `equalix-go`'s already-shipped phase history as the canonical numbering or start fresh. Worth deciding before the first `EQLX-` branch in this repo, because it's cheap now and expensive to change later.
