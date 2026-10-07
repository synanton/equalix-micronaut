# Characterization run 02 — warm RSS, three points (Spring vs Micronaut)

Date: 2026-10-07. Same host, same PostgreSQL 16 container, default JVM heap on both
sides (same ergonomics — same host, same JDK 21). Workload: `w2000.jsonl` from
`equalix-go/test/differential` (2000 tasks, tenants a/b/c weights 1/2/7), paced over
60 s against a 5 ms stub executor. Order alternated MN,Spring,MN,Spring to balance
page-cache effects. DB wiped between runs. RSS = mean of 5 `ps` samples. n=2 per side.

## RSS (MiB)

| Point | Micronaut A | Micronaut B | Spring A | Spring B |
|---|---|---|---|---|
| Ready (first REST-200) | 450 | 572 | 610 | 612 |
| +60 s idle | 717 | 806 | 639 | 644 |
| +60 s under load (w2000) | 665 | 696 | 795 | 837 |

## Read

- **Under load, Micronaut is lower in both passes** (~680 vs ~816 MiB mean, ≈−17%).
  The only cell that separates beyond run-to-run noise.
- **Ready and idle are within noise.** MN ready swings 450→572 across identical runs;
  MN idle swings 717→806. Spring is tighter (610→612, 639→644) but that tightness is
  itself a single-pair observation.
- **n=2 is not citable for deltas.** Two passes establish the harness and the shape
  (load favors Micronaut, rest is noise). A citable delta needs n≥5 per side and
  ideally pinned heap (`-Xmx` equal on both) to separate framework footprint from
  heap-growth behavior. Recorded here so the next run extends rather than repeats.

## Incidental parity signal

`currentRps` converged to exactly `5.516015367592257` in all four runs on both
sides — the adaptive controller is deterministic given the paced workload, and both
implementations compute it identically. (Not a comparison claim; just evidence the
load phase drove both schedulers through the same adaptation trajectory.)

## Method notes

- Readiness = first REST-200 on `/api/v1/status` on both sides (Spring's
  `/actuator/health` stays 503 DOWN without a broker — see char-01).
- First run's DB wipe is a no-op (migrations run at boot, after the wipe); runs 2–4
  wiped properly. No cross-run contamination: separate fairness keys per run would
  be cleaner; the truncate covers it.
- Stub executor parses the binary envelope (asserts UUID match) and completes
  after 5 ms; 2000/2000 submitted in all runs.
