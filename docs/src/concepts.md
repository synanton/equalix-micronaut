# Concepts

Equalix schedules tasks across **fairness keys** — logical tenant groups — with
weighted fairness, adaptive throttling, and crash recovery. This page explains the
core ideas; the [task lifecycle](lifecycle.md) covers state transitions and the
[configuration](configuration.md) page covers every knob.

## Fairness keys and weights

Every task carries a `fairnessKey` (the tenant) and a positive `weight` (default
1.0). A key with weight 2 receives twice the dispatch share of a key with weight 1
under equal conditions.

## Virtual time and priority

Each fairness key has a persistent virtual time. When the priority calculator tags
a task, it reserves a **finish tag**:

```
finishTag = max(key.virtualTime, systemV) + quantum / weight
priority  = round(finishTag) + inFlightCount × penaltyFactor / weight
```

Keys with many in-flight tasks accumulate virtual time faster, so their next tasks
sort behind lighter keys — proportional fairness without any `COUNT(*)` on the hot
path.

## Count-Min Sketch

In-flight counts are estimated by a Count-Min Sketch (default 65536×5, ~2.6 MB):
O(1) updates and reads, fixed memory, bounded overestimation. The watchdog
reconciles the sketch against the real task table periodically and rebuilds it from
the corrected snapshot.

## Adaptive RPS

The controller watches completion latency and error rate against a target
(default 200 ms ± 20%). Latency above the band decreases RPS; latency below it
(with low errors) increases RPS. An emergency brake halves RPS when the error rate
crosses its threshold.

## Hierarchical fairness

In `hierarchical` mode, fairness keys are paths (`acme/sales`) and every layer is
scheduled fairly among its siblings — organization first, then department, then
leaf. Per-node weight overrides are supported.

## Anti-starvation

Aging policies (`none`, `linear`, `log`, `power`) subtract a growing credit from
priority for long-waiting tasks. The `max-queued-time-ms` deadline is a hard
backstop: tasks past it are promoted to the front regardless of policy.

## Sequential execution

Sequential tasks for one fairness key run one at a time in `sequenceNumber` order.
A blocked client (no completion within `client-block-timeout-ms`) is recovered by
the block-recovery job; dependent tasks wait for their predecessor's result.
