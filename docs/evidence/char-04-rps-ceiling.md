# Characterization run 04 — RPS ceiling + time-to-ceiling (Spring vs Micronaut)

Date: 2026-10-07. Same host, same PostgreSQL 16, default heap both sides (G1).
Workload: `w2000.jsonl` **burst**-ingested (~1.5 s, 16-way concurrent) against a
100 ms stub — same file as char-02/03, different injection profile (backlog-driven
instead of paced; stated here so pacing is never Tacit). `max-rps` raised to 1000
so the ceiling is systemic (DB-bound), not config. Order MN,Spring,Spring,MN. n=2.

## Stated prior

- Naive: "Micronaut handles higher RPS at ceiling (AOT)." Expected wrong — the DB
  is the bottleneck, not the DI framework.
- Main: "No RPS difference at ceiling; both saturate at the same DB-bound
  throughput." Testable; a confirming null bounds the AOT advantage further.
- Sub-hypothesis: "Micronaut reaches ceiling faster (shorter time-to-steady-state)
  even if the ceiling matches." Measured three ways, reported three ways, gated on
  none — characterization describes, it doesn't assert.

## (a) Time for `currentRps` → 95% of steady state (controller-side)

| | Micronaut A | Micronaut B | Spring A | Spring B |
|---|---|---|---|---|
| Steady RPS | 109.1 | 103.9 | 103.9 | 89.1 |
| t95 | 32 s | 32 s | 30 s | 34 s |

No ordering: 30–34 s on both sides.

## (b) Time for dispatch rate → 95% of steady (completions/s per 10 s window, DB timestamps)

| Window | MN-A | MN-B | Spring-A |
|---|---|---|---|
| w0–10 s | 8 | 3 | 8 |
| w10–20 s | 50 | 48 | 47 |
| w20–30 s | 69 | 67 | 64 |
| w30–40 s | 72 | 80 | 80 |

(Spring-B rate windows were lost to a per-run DB wipe before extraction; its RPS
series above is retained. Do not read the missing column as a finding.)
Both sides plateau ≈70–80/s with the same ramp shape. No ordering.

## (c) RPS ramp slope, first 30 s (50 → value)

MN-A 1.63/s, MN-B 1.48/s, Spring-A 1.63/s, Spring-B 0.90/s. One slow Spring run,
otherwise tied. No consistent ordering.

## Verdict

**Ceiling equal (~75/s, DB-bound), time-to-ceiling equal within noise.** The naive
prior is refuted as expected; the interesting sub-hypothesis does not hold
either — startup-to-throughput shows no Micronaut advantage at n=2. Together with
char-01–03, the AOT advantage is now bounded on four sides: it lives in
spawn→ready and image size, nowhere in sustained runtime behavior measured so far.

## Method notes

- Burst ingestion reintroduces the commit race at scale: slow multi-row dispatch
  commits widen the window where a fast completion lands pre-commit and 400s
  (Micronaut 12–15 stuck/timeout per run vs Spring 0 — same race, wider window
  under burst; the §13 NOTE covers the mechanism). Throughput comparisons use
  completed counts, which are unaffected.
- RPS-limit values (≈90–109) sit above actual throughput (~75/s): the controller
  is not the binding constraint here, dispatch mechanics are.
- Same caveats as char-03: G1/default heap both sides, 100 ms stub in the RPS
  deadband, n=2.
