# equalix-micronaut

> A third implementation of the Equalix scheduler — Java + Micronaut — built to characterize JVM startup, image size, and runtime footprint alongside the Spring Boot oracle and the Go reimplementation.

[![Java Version](https://img.shields.io/badge/java-21-ED8B00?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Micronaut](https://img.shields.io/badge/micronaut-4.x-1B6E8C)](https://micronaut.io)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-pre--alpha-orange.svg)](#status)

`equalix-micronaut` is a spec-first reimplementation of [Equalix](https://github.com/synanton/equalix) in Java + [Micronaut](https://micronaut.io), built as a **third comparison point** in the Equalix family. Its scheduling semantics are the same as the Spring Boot oracle by construction — same schema, same REST contract, same executor protocol. Its reason to exist is measurement, not novelty.

---

## Status

**Alpha.** Implemented and smoke-tested; not yet differentially validated.
See [`docs/PORTING.md`](docs/PORTING.md) for the Spring→Micronaut mapping, the
framework findings, and the intentional deviations.

**State as of:** `78eabf2` (2026-10-08) — full-parity port builds, 250/250
tests green (179 oracle unit tests verbatim + 4 Spring-coupled tests ported to
Micronaut idioms, plus starvation-bypass, write-path and concurrency parity tests), boots against
PostgreSQL 16 with Flyway migrations,
REST contract smoke-tested (create → RECEIVED → QUEUED → DISPATCHED → SUCCEEDED,
auth, validation envelopes, 404s, `/health`, `/api/v1/status`).
**Oracle:** Spring Boot Equalix ([equalix](https://github.com/synanton/equalix));
parity scope and deviations are tracked in [`docs/PORTING.md`](docs/PORTING.md).
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
- **Same REST contract** — the oracle's [API reference](https://github.com/synanton/equalix/blob/main/docs/api-reference.md) describes the endpoints; the Micronaut implementation serves the same paths, bodies, and status codes.
- **Same executor protocol** — `POST /tasks/{id}/execute` out, `POST /api/v1/tasks/{id}/complete` back. Same shape as the other two.
- **Same domain semantics** — the virtual-time formula, priority calculation, CMS behavior, adaptive RPS, watchdog, and timeout sweep follow the oracle without reinterpretation (see [`docs/PORTING.md`](docs/PORTING.md)). Where Micronaut idioms differ from Spring idioms, the *behavior* is what's preserved.

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

Four characterization runs and three differential pairs converge on one story:
**AOT's advantage is bounded to cold start and image size — it does not extend
to runtime profile.** The full matrix with cited numbers lives at the family
level — this repo carries the port and its per-phase evidence, not a second
copy of the numbers:

[**Equalix family comparison →**](https://github.com/synanton/.github/blob/main/profile/experiments/equalix-family-comparison.md)

Provenance (Go-vs-Spring = independent reproduction; Spring-vs-Micronaut =
port fidelity across seams; Go-vs-Micronaut = cross-runtime agreement) and
caveats (drain tolerance, warmup asymmetry, explicit outs) are stated there,
alongside the stopping-point declaration.

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
| Domain core | ✅ | ✅ | ✅ (1:2:7 shares, virtual time) | ✅ (dispatch path at parity, char-03/04) | ✅ jmn + gomn warm-class shares |
| PostgreSQL adapter | ✅ | ✅ | ✅ (same suites) | ⬜ | ✅ warm-class (same runs) |
| HTTP surface | ✅ | ✅ | ✅ (`TaskIngestionIntegrationTest` end-to-end; envelope/validation unit tests) | ⬜ | ✅ warm-class (same runs) |
| Jobs (dispatcher, calculator, watchdog, timeout) | ✅ | ✅ | ✅ (fairness suites drive the real jobs) | ⬜ | ✅ warm-class (same runs) |
| Adaptive RPS | ✅ | ✅ | ⬜ | ⬜ | ✅ warm-class (same runs; endpoint convergence char-02) |
| Container image | ✅ | ⬜ | ⬜ | ✅ (char-01: 386 vs 416 MB) | ⬜ |
| Startup profile | ✅ | ⬜ | ⬜ | ✅ (char-01: 3.21 vs 5.18 s spawn→serving) | ⬜ (recorded, never gated) |

`⬜` = not yet; nothing here is claimed before its evidence exists — same rule as the
[Go maturity table](https://github.com/synanton/equalix-go#maturity).
`Implemented` is ✅ where the code exists and builds; `Tested` is ✅ where unit +
integration suites run green. Test counts below are re-verified on every change touching
`src/`; validation columns cite committed artifacts only.

### Conformance (Testcontainers PostgreSQL, scheduling off, jobs driven explicitly)

- `ProportionalFairnessIntegrationTest` — weights 1:2:7 continuously backlogged over
  10,000 dispatches: every tenant within 2 tasks of its weighted share in every
  window (`ε_max ≤ 2/|W|`, prefix + sliding, `W ∈ {10, 25, 100, 1000, 10000}`).
- `HierarchicalFairnessIntegrationTest` — per-level shares, node virtual-time
  charges, idle-restart floors.
- `VirtualTimeIntegrationTest` — finish-tag assignment and system virtual time.
- `StarvationBypassIntegrationTest` + `shouldServePromotedTasksBeyondQuota` —
  CORRECTION-7 backstop, flat and hierarchical.

### Benchmark (characterization, `docs/evidence/char-0*`, 2026-10-07, n=2 unless noted)

- char-01 — image 386 vs 416 MB; spawn→serving 3.21 vs 5.18 s; first dispatch
  identical within noise (0.37 vs 0.40 s, tick-dominated).
- char-02 — warm RSS under load ≈680 vs ≈816 MiB (−17%); ready/idle within noise.
  n=2 establishes harness + shape, not a citable delta.
- char-03 — GC/latency null result: end-to-end p99 bands overlap, G1 paused time
  <0.15% on both sides. GC is not where Micronaut wins.
- char-04 — RPS ceiling equal (~75/s, DB-bound), time-to-ceiling equal within noise.

Convergent story (also the [family comparison](https://github.com/synanton/.github/blob/main/profile/experiments/equalix-family-comparison.md)):
AOT's advantage is bounded to cold start and image size — nowhere in sustained
runtime behavior measured so far.

### Differentially validated vs Spring Boot

Differential evidence lives in `equalix-go` and is cited here by repo + path, never
copied ([methodology](https://github.com/synanton/equalix-go/blob/main/docs/differential-methodology.md),
[runs](https://github.com/synanton/equalix-go/blob/main/docs/evidence/threeway/README.md)).
Pairs involving this implementation (2026-10-07, workload `w2000.jsonl`
`68741187…9546`, 100 ms stub, warm class throughout):

| Pair | Claim | Status |
|---|---|---|
| Spring-vs-Micronaut | Direct port preserves oracle semantics across seams | pass, no mismatch |
| Go-vs-Micronaut | Cross-runtime agreement (not independent convergence) | pass, no mismatch |

Gates: warm-class shares (±2/1000-window); dispatch order diagnostic only (flaky by
construction); stuck sends excluded from gates, reported per side with the client
stack named; seams-checked, not assumed (per-tick batch sizes 4.3 vs 4.2, p99 tick
149 vs 140 ms, commit-race rejects 0/0 both sides).
Pinned SHAs: Spring `11ef025e`, MN `b12176c0`, Go `f8a2a21e`.
Post-pin delta (CORRECTION-7 bypass on all three sides + schema squash): proven
no-op on the gate workload (`promoted = 0` in every recorded trace), so the pass
verdicts transfer to current HEADs; the bypass path itself is covered by the
conformance tests above. A re-run at current SHAs is the remaining sign-off —
full analysis in the [three-way addendum](https://github.com/synanton/equalix-go/blob/main/docs/evidence/threeway/README.md#post-run-deltas-addendum-2026-10-09).

---

## Roadmap

See the phase list below; per-repo scope notes live in [`docs/PORTING.md`](docs/PORTING.md). The family's phase numbering continues from `equalix-go`'s EQLX-0 through EQLX-7:

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
  -e EQUALIX_JDBC_URL=jdbc:postgresql://host:5432/equalix \
  -e EQUALIX_DB_USER=equalix \
  -e EQUALIX_DB_PASSWORD=equalix \
  -p 8080:8080 \
  equalix-micronaut:local
```



------

## Contributing

Same discipline as the rest of the family:

- Conventional commits with phase scope
- `EQLX-` branch prefix (shared across the family)
- Training gate (craft) and production gate (evidence) — same discipline as the [oracle's guidelines](https://github.com/synanton/equalix/blob/main/CONTRIBUTING.md)
- Freshness marker bumps on phase transitions only
- DECISION / CORRECTION / NOTE taxonomy for spec §13 entries

------

## Family

Equalix has three implementations of the same scheduling semantics:
Spring Boot ([equalix](https://github.com/synanton/equalix), the reference),
Go ([equalix-go](https://github.com/synanton/equalix-go)), and Micronaut
([equalix-micronaut](https://github.com/synanton/equalix-micronaut)).

Startup, footprint and runtime characterization across all three:
[**Equalix family comparison →**](https://github.com/synanton/.github/blob/main/profile/experiments/equalix-family-comparison.md).

Published developer books:
[Equalix](https://synanton.github.io/equalix/) (the oracle),
[equalix-go](https://synanton.github.io/equalix-go/).
Three-way differential evidence (all pairs, pinned SHAs, transfer addendum):
[equalix-go `docs/evidence/threeway/`](https://github.com/synanton/equalix-go/tree/main/docs/evidence/threeway/).

## License

Apache License 2.0. See [`LICENSE`](https://license/).

------

## Acknowledgments

Third implementation in the Equalix family. Oracle: [equalix](https://github.com/synanton/equalix) (Spring Boot). Sibling: [equalix-go](https://github.com/synanton/equalix-go).
