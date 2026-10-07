# Characterization run 03 — GC pauses + end-to-end latency (Spring vs Micronaut)

Date: 2026-10-07. Same host, same PostgreSQL 16, default JVM heap both sides
(G1, JDK 21). Workload: `w2000.jsonl` paced over 60 s against a 100 ms stub
executor. Order MN,Spring,Spring,MN. n=2 per side.

## Stated prior (null hypothesis)

Micronaut's AOT removes reflection and context-scan cost — startup and footprint
(char-01/02) — not allocation rate. GC pressure is a function of allocation, so
**no significant difference in GC pause profile or end-to-end latency is expected
between the two JVMs.** A null result confirming this is itself the finding: it
bounds where the AOT advantage lives.

## End-to-end latency, create → complete (ms, stub contributes ~100 ms floor)

| | Micronaut A | Micronaut B | Spring A | Spring B |
|---|---|---|---|---|
| n completed | 1869 | 1874 | 2000 | 2000 |
| p50 | 205 | 206 | 197 | 202 |
| p99 | 290 | 306 | 325 | 310 |
| max | 401 | 470 | 413 | 359 |

No separation: p99 bands overlap (290–306 vs 310–325) at n=2. Consistent with the
null — dispatch latency is scheduler-tick + stub dominated on both sides.

## G1 stop-the-world pauses during the runs (ms, `-Xlog:gc`)

| | Micronaut A | Micronaut B | Spring A | Spring B |
|---|---|---|---|---|
| Pause count | 37 | 71 | 46 | 45 |
| p50 | 2.74 | 2.94 | 3.23 | 3.22 |
| p99 | 11.1 | 12.6 | 13.6 | 14.1 |
| Total paused | 144 | 233 | 162 | 167 |

No significant difference: p99 within ~15%, totals under 250 ms over ~3 min runs
(<0.15% paused time) on both sides. Pause *count* swings 37→71 between Micronaut's
own runs, so count-derived claims would need far more samples. Verdict: **null
hypothesis holds — GC is not where Micronaut wins.**

## Method notes (load-bearing for reuse)

- **Stub latency is a load-shaping parameter, not neutral.** 5 ms completions race
  the dispatch transaction commit and are correctly rejected with 400
  (not-in-flight yet) — that rejection is correct protocol behavior on both
  implementations, but it pollutes throughput. 2000 ms completions look like a
  slow executor and collapse the adaptive RPS (backlog latencies 10–60 s).
  100 ms sits under the RPS deadband (target 200 ms ± 20%) so RPS holds steady
  and latencies measure dispatch mechanics. The differential stub must account
  for the commit race the same way.
- **Drain asymmetry (observation, not claim):** Micronaut hit the 60 s drain
  timeout twice (131/126 in flight); Spring drained fully twice. Same RPS config
  both sides; cause not investigated — worth a look before char-04's ceiling runs.
- G1 on both sides, default heap, no tuning flags. p99 latency is the portable
  cross-runtime metric; raw pause-ms must never be equated across runtimes
  (relevant when Go joins the matrix).
