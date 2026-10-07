# Characterization run 01 — image size + cold start (Spring vs Micronaut)

Date: 2026-10-07. Method: single run per side (n=1), same host, same PostgreSQL 16
container, same `eclipse-temurin:21-jre` runtime base. Micronaut image built from this
repo's `Dockerfile`; Spring image from a throwaway equivalent (`eclipse-temurin:21-jre`
+ oracle fat jar — the oracle ships no Dockerfile). Health polled every 0.5s
(±0.5s on trailing edges). Clocks: app `Instant` milestones vs host `date` (same clock).

## Image size

| Artifact | Spring Boot | Micronaut | Δ |
|---|---|---|---|
| Fat jar | 98.2 MB | 67.5 MB | −31% |
| Container image | 416 MB | 386 MB | −30 MB (−7%) |

Both dominated by the shared JRE base; the app-layer delta is the framework surface
(Spring MVC/WebFlux/Security/Kafka vs Netty/Micronaut-Data/Lettuce).

## Cold start (no broker, fresh DB per side)

| Phase | Spring Boot | Micronaut |
|---|---|---|
| Spawn → `main()` | 0.23 s | 0.11 s |
| `main()` → context ready | 4.72 s | 2.29 s |
| Ready → first 200 | 0.23 s | 0.81 s |
| **Spawn → serving** | **5.18 s** | **3.21 s** |
| Cold RSS (one sample) | 565 MiB (765 MiB on a longer-warmed run — noisy) | 623 MiB |

Micronaut context comes up ~2× faster; end-to-end spawn→serving is ~2 s quicker.
RSS single samples are inconclusive (200 MiB swing on the Spring side across runs) —
warm RSS under load needs repeated sampling (next run).

## First dispatch (task created on a ready instance → DISPATCHED)

| Side | Create → dispatched |
|---|---|
| Spring Boot | 0.40 s |
| Micronaut | 0.37 s |

Identical within noise — both dominated by the 50/100 ms scheduler ticks, not the
framework. As expected: the runtime delta is startup, not steady-state dispatch.

## Readiness-definition finding (harness-relevant)

Without a broker, Spring's `/actuator/health` reports **503 DOWN** (Kafka binder)
while the app serves REST normally; Micronaut's `/health` reports **200 UP**
(its Kafka health indicator is disabled by design, consumer retries in background).
The characterization harness must define readiness per side (health-200 vs
context-ready + REST-200), not as one shared health check. Recorded in
`PORTING.md` comparison notes.
