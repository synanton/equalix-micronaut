# Echo executor

A minimal stub executor: accepts `POST /tasks/{id}/execute`, then reports
completion back to the scheduler.

```python
import struct, threading, time, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SCHED = "http://localhost:8080"
API_KEY = "changeme"
LATENCY_MS = 100

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_POST(self):
        task_id = self.path.strip("/").split("/")[1]
        length = int(self.headers.get("Content-Length", 0))
        self.rfile.read(length)
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()
        threading.Timer(LATENCY_MS / 1000.0, complete, args=[task_id]).start()

def complete(task_id):
    body = b'{"success": true}'
    req = urllib.request.Request(
        f"{SCHED}/api/v1/tasks/{task_id}/complete", data=body,
        headers={"Content-Type": "application/json", "X-API-Key": API_KEY},
        method="POST")
    urllib.request.urlopen(req, timeout=10).read()

ThreadingHTTPServer(("127.0.0.1", 9090), Handler).serve_forever()
```

Run it, then point the scheduler at it:

```bash
EQUALIX_JDBC_URL=jdbc:postgresql://localhost:5432/equalix \
EQUALIX_DB_USER=equalix EQUALIX_DB_PASSWORD=equalix \
EQUALIX_API_KEY=changeme \
java -jar target/equalix-micronaut-1.0.0-SNAPSHOT.jar
```

Tasks now flow end to end: `RECEIVED → QUEUED → DISPATCHED → COMMITTED → SUCCEEDED`.

> **Stub latency is a load-shaping parameter.** 100 ms sits under the adaptive
> RPS deadband (target 200 ms ± 20%). Much faster stubs race the dispatch
> transaction commit; much slower ones read as executor slowness and collapse
> the controller. See the family comparison's methodology notes.
