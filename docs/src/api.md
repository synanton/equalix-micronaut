# REST API

All endpoints except `/health` and `/info` require the `X-API-Key` header matching
`app.security.api-key`. Errors share one envelope:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-10-07T06:08:38.272110599Z",
  "fieldErrors": [{"field": "fairnessKey", "message": "must not be blank"}]
}
```

## Create task

```
POST /api/v1/tasks
```

| Field | Type | Notes |
|---|---|---|
| `fairnessKey` | string | required, non-blank |
| `weight` | decimal | default 1.0, must be positive |
| `payload` | string | base64-encoded bytes, required |
| `sequential` | boolean | default false |
| `sequenceNumber` | long | required when `sequential` is true |
| `dependsOnTaskId` | UUID | optional predecessor |
| `requiresPreviousResult` | boolean | wait for predecessor's result |

Returns `201` with the task ID as a JSON string.

## Get task

```
GET /api/v1/tasks/{taskId}
```

Returns the task status (id, fairnessKey, status, priority, createdAt,
completedAt, retryCount, lastError). `404` when unknown.

## List tasks by fairness key

```
GET /api/v1/tasks?fairnessKey={key}&status={status}
```

`fairnessKey` is required; `status` is an optional filter.

## Complete task (executor webhook)

```
POST /api/v1/tasks/{taskId}/complete
```

| Field | Type | Notes |
|---|---|---|
| `success` | boolean | required |
| `result` | string | base64-encoded bytes |
| `error` | string | required when `success` is false |

## System status

```
GET /api/v1/status
```

Returns `{"inFlight": N, "currentRps": R}` — the CMS total in-flight estimate and
the adaptive controller's current RPS.

## Health and info

```
GET /health
GET /info
```

Unauthenticated. `/health` reports `{"status":"UP"}` once the context is ready.
