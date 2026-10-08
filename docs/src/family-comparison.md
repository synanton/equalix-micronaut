# Family comparison

Equalix has three implementations of the same scheduling semantics: Spring Boot
([`equalix`](https://github.com/synanton/equalix), the reference), Go
([`equalix-go`](https://github.com/synanton/equalix-go)), and Micronaut (this
repository). The canonical three-implementation characterization — startup,
footprint, runtime, and the differential results — lives at:

**[Equalix family comparison →](https://github.com/synanton/.github/blob/main/profile/experiments/equalix-family-comparison.md)**

The headline finding: **AOT's advantage is bounded to cold start and image
size — it does not extend to runtime profile.** GC pauses, p99 latency,
sustained ceiling, and time-to-ceiling all overlap within measurement noise
between the two JVMs.

## What this port is for

`equalix-micronaut` exists to fill the third column of that matrix — separating
the runtime variable (JVM vs. compiled) from the framework variable (Spring's
reflection-heavy context vs. Micronaut's AOT DI). The scheduling semantics are
pinned to the oracle by construction; the numbers are the deliverable.

## Evidence index

- `equalix-go/docs/evidence/char-01-image-startup.md` — image size, cold start
- `equalix-go/docs/evidence/char-02-warm-rss.md` — warm RSS at three points
- `equalix-go/docs/evidence/char-03-gc-latency.md` — GC pauses, p99 latency
- `equalix-go/docs/evidence/char-04-rps-ceiling.md` — ceiling, time-to-ceiling
- `equalix-go/docs/evidence/threeway/` — three differential pairs at one
  provenance
- `equalix-go/docs/differential-methodology.md` — provenance, gates, seams,
  methodology entries

## Stopping point

The family is at a legitimate stopping point: oracle stable, Go released with
all claims cited through EQLX-9, Micronaut ported and characterized. Everything
remaining (livelock fix, phase-lock jitter experiment, out-rows,
GraalVM native column, drain-tolerance gate fix) is deferred and named —
chose-not-to, not should-have.
