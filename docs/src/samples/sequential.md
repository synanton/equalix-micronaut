# Sequential pipeline

Sequential tasks for one fairness key run strictly in `sequenceNumber` order —
one in flight at a time.

```bash
for seq in 1 2 3; do
  curl -X POST http://localhost:8080/api/v1/tasks \
    -H "X-API-Key: changeme" -H "Content-Type: application/json" \
    -d "{
      \"fairnessKey\": \"pipeline-client\",
      \"weight\": 1.0,
      \"payload\": \"c2Vx${seq}=\",
      \"sequential\": true,
      \"sequenceNumber\": ${seq},
      \"requiresPreviousResult\": false
    }"
done
```

With the [echo executor](executor.md) running, task 1 dispatches; tasks 2 and 3
wait until each predecessor completes. The sequential dispatcher advances the key
one task per completion.

## Dependency pass-through

A task with `dependsOnTaskId` and `requiresPreviousResult: true` waits for its
predecessor's result. If the predecessor fails, the dependent fails with
`Dependency failed: <error>` — the result-passthrough recovery job handles this
without executor involvement.

## Blocked clients

If a sequential task never completes, the key is blocked. The block-recovery job
(after `client-block-timeout-ms`, default 60 s) unblocks the key so the pipeline
can advance.
