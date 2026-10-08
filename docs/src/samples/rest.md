# REST ingest

Create a task:

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "X-API-Key: changeme" \
  -H "Content-Type: application/json" \
  -d '{
    "fairnessKey": "acme",
    "weight": 1.0,
    "payload": "aGVsbG8=",
    "sequential": false,
    "requiresPreviousResult": false
  }'
```

Response: `201` with the task ID.

Fetch it:

```bash
curl http://localhost:8080/api/v1/tasks/<taskId> -H "X-API-Key: changeme"
```

```json
{
  "id": "8cf1b3f8-9ff5-41da-8d76-4231c1a4e110",
  "fairnessKey": "acme",
  "status": "RECEIVED",
  "priority": null,
  "createdAt": "2026-10-07T06:08:38.299847Z",
  "completedAt": null,
  "retryCount": 0,
  "lastError": null
}
```

Within a few ticks the priority calculator tags it (`QUEUED`) and the dispatcher
sends it (`DISPATCHED`). Without an executor wired, it stays in flight until the
timeout sweep.

List by fairness key:

```bash
curl "http://localhost:8080/api/v1/tasks?fairnessKey=acme" -H "X-API-Key: changeme"
```

Validation failures return `400` with the field-level envelope — see the
[REST API](api.md) page.
