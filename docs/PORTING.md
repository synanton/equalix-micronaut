# Porting notes — Spring Boot oracle → Micronaut

Scope: full-parity port of `equalix` (Spring Boot 3.5, Java 21) to Micronaut 4.x.
Package `org.synanton.equalix` kept identical so files diff cleanly against the oracle.
The migration set is the oracle's squashed `V1__baseline.sql`, byte-identical —
schema divergence would invalidate every comparison. (Micronaut validates against it
via `hbm2ddl.auto: validate`, the counterpart of the oracle's `ddl-auto: validate`.)

Verified so far: `mvn test` 250/250 green — the 179 oracle unit tests that don't need Spring/Testcontainers (copied verbatim),
4 Spring-coupled tests ported to Micronaut idioms (see below), and all 12 oracle
integration tests ported to Micronaut Test + Testcontainers (see below); app boots against PostgreSQL 16, Flyway migrates,
all schedulers tick; REST contract smoke-tested end to end
(create → RECEIVED → QUEUED → DISPATCHED → complete → SUCCEEDED, auth, validation
envelopes, 404s, `/health`, `/api/v1/status`).

## The 4 Spring-coupled tests (all ported — no coverage gap)

- `adapter/out/executor/HttpRemoteExecutorAdapterTest` — wire protocol behavior
  (path, method, content type, 16+4+n+4+m envelope layout, ack-on-2xx, silence on
  5xx/network failure). Oracle-only coupling was the WebClient exchange-function
  double; ported to an in-JDK `HttpServer` stub (5 tests, one added: byte-level
  envelope decode).
- `adapter/out/cms/TransactionAwareCmsProviderTest` — tx-buffer semantics
  (immediate outside tx, net deltas on commit, discard on rollback, per-session
  REQUIRES_NEW isolation, delegate-failure tolerance). Oracle-only coupling was
  Spring's `TransactionSynchronizationManager` test seam; ported to
  Mockito-mocked Hibernate `SessionFactory`/`ActionQueue` driving
  `AfterTransactionCompletionProcess` (8 tests, one added: inactive-tx passthrough).
- `adapter/out/cms/RedisCMSAdapterTest` — key layout (`ns:v2` hash, `:total`),
  min-across-rows reads with clamp/negative/null rules, Lua add arg shape,
  rebuild del+repopulate+total (sequential `HINCRBY`, not pipelined), no-fallback
  failure mode. Oracle-only coupling was `StringRedisTemplate` mocks; ported to
  Lettuce `StatefulRedisConnection`/`RedisCommands` mocks (15 tests).
- `adapter/in/rest/GlobalExceptionHandlerTest` — envelope codes/bodies
  (NOT_FOUND, VALIDATION_FAILED + fieldErrors, BAD_REQUEST, INTERNAL_ERROR) and
  the API-key gate (401 without/wrong key, passthrough with key, `/health` +
  `/info` open). Oracle-only coupling was MockMvc + Spring Security chain; ported
  to direct handler/filter unit tests with real bean validation (10 tests).

## Framework mapping

| Oracle (Spring) | This port (Micronaut) | Notes |
|---|---|---|
| `@Service` / `@Component` | `@Singleton` (+ explicit `@Inject` ctor, see below) | |
| `@Transactional` (Spring) | `io.micronaut.transaction.annotation.Transactional` | Same boundaries (domain services) |
| `@ConfigurationProperties(prefix=)` | `@ConfigurationProperties("…")` | Nested single POJOs must be **static nested + relative path** (Micronaut's own `CorsConfiguration` pattern); top-level nested types are silently ignored |
| `@NestedConfigurationProperty` | _(dropped)_ | Plain nesting binds by field name |
| `@ConditionalOnProperty` | `@Requires(property=, value=)` | |
| `@RestController` + `ResponseEntity` | `@Controller` + `HttpResponse` | Same paths/bodies/statuses |
| `@RestControllerAdvice` | One `ExceptionHandler<T, HttpResponse<?>>` `@Singleton` per type | Same `code/message/timestamp/fieldErrors` envelope; `ValidationHandler` is `@Primary` (beats Micronaut's built-in) |
| API-key `OncePerRequestFilter` + Spring Security | `HttpServerFilter` (`@Filter("/**")`, 401 otherwise; `/health`, `/info` open) | Same `X-API-Key` contract |
| Spring Data `JpaRepository` | Micronaut Data `GenericRepository` + explicit `@Query` | See "persistence" below |
| `@Scheduled(fixedDelayString)` + ShedLock `@SchedulerLock` | `@Scheduled(fixedDelay="…ms")` / `fixedRate="…m"`, **no distributed lock** | ShedLock 5.x ships no Micronaut-4 integration; single-instance harness doesn't need it. Re-add before horizontal scaling |
| `WebClient` (Reactor, fire-and-forget) | JDK `HttpClient.sendAsync` | Same wire bytes, same non-blocking semantics, zero framework coupling. Pinned to HTTP/1.1 (no h2c upgrade dance against simple executors). Known harness edge, not production-verified against all servers: under sustained concurrency the JDK pooled client occasionally reads an empty response where Reactor Netty does not (~0.3–5% depending on stub); the Go differential stub is the arbiter |
| `StringRedisTemplate` + Lua | Lettuce `StatefulRedisConnection` + `EVAL` | Same keys, same script, same local fallback |
| Spring Kafka `@KafkaListener` + `Acknowledgment` | Micronaut `@KafkaListener` + `@Topic` (offset commits after return; throw → redelivery) | Same at-least-once contract |
| Micrometer (same meter names) | Micronaut Micrometer (same `MeterRegistry` API) | Metric names untouched |
| Spring `TransactionSynchronizationManager` | Hibernate `ActionQueue.AfterTransactionCompletionProcess` per session | Same commit-applies / rollback-discards CMS semantics |
| `Clock.systemUTC()` bean | `@Factory` + `@Singleton Clock` | |

## Findings that cost real time (read before touching these areas)

1. **Lombok must be FIRST on `annotationProcessorPaths`.** Otherwise Lombok
   transformations are invisible to Micronaut's annotation processor (beans get
   no-arg definitions → `NoSuchMethodError` at startup), while javac itself is
   fine. Symptom: `method 'void <init>()' not found`.
2. **No Lombok-generated members for DI/introspection.** Micronaut reads sources,
   not Lombok output. All bean constructors are explicit `@Inject` (scripted from
   `@RequiredArgsConstructor`); all `@ConfigurationProperties` classes use explicit
   getters/setters; request DTOs use explicit accessors (else validation silently
   passes). `@Data` remains only where runtime reflection suffices (Jackson,
   tests, entities).
3. **`@ConfigurationProperties` needs `@Introspected` on every nested POJO**
   (else `No bean introspection present` at validation).
4. **Micronaut Data has no `@Modifying`/`@Param`.** Modifying queries are
   auto-detected; named parameters bind to method parameter names — several
   repository signatures rename params to match placeholders (`fairnessKey`→`key`
   etc.). The `FOR UPDATE … SKIP LOCKED` locking reads live in
   `TaskLockingQueries` (SessionFactory native queries) because the Data query
   parser misclassifies `FOR UPDATE` as a mutation.
5. **Spring `save()` merges versioned entities; Micronaut Data persists.**
   Read-then-save in one transaction threw `NonUniqueObjectException`, so
   `TaskRepositoryAdapter` / `ClientSequenceStateRepositoryAdapter` merge via the
   current session when a transaction is active (exact Spring semantics, including
   the extra SELECT on create).
6. **`snakeyaml` must be declared** (runtime) or `application.yml` silently loads
   nothing (datasource, app config — everything — stays default).
7. **Jackson parity needs explicit config**: `serialization-inclusion: ALWAYS`
   (nulls present, like the oracle) and `serialization.write-dates-as-timestamps: false`
   (ISO-8601 `Instant`s).
8. **Disable `kafka.health.enabled`** when no broker is present, or `/health`
   fails (the consumer itself retries harmlessly, like the oracle).
9. **Native locking reads may not see the same tick's promotion write.**
   `promoteStarvedTasks` saves priority 0 and the tick's `FOR UPDATE …
   SKIP LOCKED` select runs in the same transaction; a native SELECT need
   not observe the unflushed write, so a promoted task can dispatch one
   tick later here while the oracle dispatches it the same tick. No
   steady-state effect (the next tick converges), but
   `StarvationBypassIntegrationTest` drives two ticks to stay
   deterministic on both frameworks.

## Intentional deviations from the oracle

- **Env vars renamed to neutral `EQUALIX_*`** (`EQUALIX_JDBC_URL`, `EQUALIX_DB_USER`,
  `EQUALIX_DB_PASSWORD`, `EQUALIX_API_KEY`, `KAFKA_BOOTSTRAP_SERVERS` kept). Same
  defaults as the oracle; see `docker-compose.yml`.
- **No ShedLock** (see table). The `shedlock` table ships in the `V1__baseline.sql`
  schema for future use; no lock is taken.
- **No Kafka in default compose** — app runs without a broker; set
  `KAFKA_BOOTSTRAP_SERVERS` to wire one.
- Management paths are Micronaut's (`/health`, `/info`, `/prometheus`) rather than
  `/actuator/*`; only `/health`, `/info` are public, same policy as the oracle.

## Write-path optimizations (oracle O1–O3, O6 ported)

Same changes as the oracle's `perf/db-load-reduction` branch, same semantics:
bulk dispatch UPDATE, targeted queue/ack/completion/timeout UPDATEs with in-statement
guards, bulk starvation promotion, per-key batched `client_counts` increments, and
single-statement sequence-state `findOrCreate` (session native query with
`RETURNING *`, same execution path as `TaskLockingQueries` — Micronaut Data's query
parser is not involved).

Deliberate port differences from the oracle's patch:

- **No `insert()` path (already optimal).** The oracle added `persist()`-based ingest
  because Spring Data's merge costs a SELECT-miss per new row (800 stmts/400 tasks).
  Micronaut Data `save()` persists new rows directly — measured 400/400 before the
  patch — so the port keeps its save path for ingest.
- **The `@Transactional` on `createTask` (needed by the session-native `findOrCreate`
  upsert) silently rerouted ingest from Data `persist()` to session `merge()`** —
  a `select … where id=?` appeared before every INSERT (800/400, caught by the
  benchmark, invisible without statement counting). Fixed with an explicit
  `insert()` (session `persist`, same single statement) on the port; `createTask`
  keeps the atomic ingest-plus-bootstrap transaction.
- **O4 does not apply.** There is no ShedLock here to gate — single-instance runs
  no lock traffic by construction. Multi-instance still needs locking first.
- **`@Transactional` on `createTask`.** The session-native `findOrCreate` needs a
  transaction-bound session; the use case previously ran without one (each repository
  call in its own transaction). Now task insert + sequence bootstrap commit atomically.
- **Null parameters need `@Nullable`.** Micronaut Data rejects null query arguments
  unless declared nullable (`completeTask` with null result/error failed at runtime,
  not compile time). `io.micronaut.core.annotation.Nullable` on the repository
  parameters; the domain ports already carry jspecify `@Nullable`.
- **Test pools capped.** Every test class with distinct properties holds its own cached
  context (and pool) for the JVM run; 10-connection defaults exhaust the container's
  `max_connections` past ~10 contexts. `BaseIntegrationTest` sets max 3 / min-idle 1;
  single-threaded tests never notice.

Measured on the ported `DbLoadBenchmarkTest` (400 tasks, single tenant; exact counts
via a counting-`DataSource` `@Replaces` bean — a `BeanCreatedEventListener` attempt
first sent DataSource creation into a 23-pool spiral, see findings):

| Phase (400 tasks) | Before (stmts) | After (stmts) | Before (wall) | After (wall) |
|---|---|---|---|---|
| Ingest | 400 | 400 | 510–625 ms | 620–700 ms |
| Priority calc | 804 | 804 | 635–830 ms | 1076–1177 ms, host noise (zero count delta) |
| Dispatch | 805 | 7 | 608–720 ms | 118–124 ms |
| Executor ack | 800 | 400 | 356–410 ms | 186–285 ms |
| Completion | 1600 | 1200 | 517–573 ms | 575–655 ms, within noise (−25% counts) |
| **Total** | **4409** | **2811 (−36%)** | **~2.7–3.0 s** | **~2.7–2.8 s** |

Same conclusions as the oracle: dispatch (−99% statements) and ack (−50%) far outside
noise; calc unchanged in counts (merge on managed entities was already UPDATE-only);
totals agree with the counts while wall-clock totals sit inside host noise on this
workload. The per-statement cost here is higher than the oracle's (Data interceptors,
null validation), so the dispatch wall win is ~5× vs the oracle's ~13× for the same
statement cut.

## Concurrency hardening (oracle P1 mirror)

Same three fixes, port-shaped: deterministic lock order (sorted bulk ids, `TreeMap`
counts/virtual-time/hierarchy charges), executor sends deferred past commit, bounded
scheduler retry. Two port differences:

- **No Spring synchronization manager — new `AfterCommitPort`.** The oracle hooks
  `TransactionSynchronizationManager` directly in the service. The domain here cannot
  touch Hibernate, and Micronaut 4 provides no equivalent manager, so the port
  (`domain/port/out`, single `afterCommit(Runnable)` method) carries the seam and
  `SessionAfterCommitAdapter` implements it over the shared `SessionCallbacks`
  registrar extracted from `TransactionAwareCmsProvider` (one implementation, two
  users). Outside a transaction the adapter runs the action immediately — unit tests
  stub the port pass-through and keep their verifications unchanged.
- **Lock failures surface as `LockAcquisitionException`** (deadlock 40P01 and lock
  waits alike), so `TransientRetry` catches that instead of Spring's pessimistic-lock
  hierarchy. Schedulers call it identically.
- **A throwing after-commit hook surfaces wrapped** (`HibernateException: Unable to
  perform afterTransactionCompletion callback`, root cause preserved) where the oracle
  propagates it raw. Same contract (rows/counts/sketch agree, slot held), different
  envelope — pinned in `CmsTransactionConsistencyIntegrationTest`.
- **Ingest regression caught by the counter:** adding `@Transactional` to `createTask`
  (required by the session-native `findOrCreate`) silently rerouted ingest from Data
  `persist()` to session `merge()` — a SELECT-per-INSERT appeared (800/400). Fixed with
  an explicit `insert()` (session `persist`), restoring 400/400 with the atomic
  ingest-plus-bootstrap transaction kept.

Pinned live by `DispatchConcurrencyIntegrationTest` (4 competing dispatchers:
exactly-once sends, exact accounting, duplicate-completion and timeout-after-completion
safety) and the multi-tenant benchmark leg below.

Multi-tenant leg (10 tenants × 400 tasks, 4 racing dispatchers, plus 10 sequential
tasks): **3121 statements, 7.6 stmts/task**, dispatch-latency p50 ≈ 730 ms / p95 ≈ 832 ms.
Dispatch statements vary slightly with thread-dependent tick count (142 vs 160–164);
per-task rate is stable.

## Integration tests (all 12 oracle files ported — no gap)

`@SpringBootTest` + `@MockBean` + `jdbc:tc:` → `@MicronautTest(transactional = false)` +
`@MockBean` + static Testcontainers PostgreSQL shared by all classes. Scheduling off
via test properties; jobs driven explicitly. MockMvc → blocking `HttpClient`
(`/actuator/prometheus` → `/prometheus`); `@TestPropertySource` folds into the
per-class `TestPropertyProvider` map. `CmsWarmUpListener` keeps its event parameter
(Micronaut requires it); the test passes a synthetic `StartupEvent`.

Framework findings from this pass: `TestPropertyProvider` on a shared abstract base
is not picked up — each concrete test class implements it (datasource URL from the
container holder, which self-starts on first use); `@Property` loses to provider-map
values, so per-class overrides live in the provider map; untyped NULL query params
need explicit types for PostgreSQL (`FOR UPDATE` readers and multi-column natives
already bypass Micronaut Data via `TaskLockingQueries`).

## Comparison phase (EQLX-7)

Characterization evidence lives in `docs/evidence/` (`char-01` image startup,
`char-02` warm RSS, `char-03` GC/latency, `char-04` RPS ceiling); the family matrix
with cited numbers is canonical at the family level (see README). Remaining for
a later pass:

- N-way differential harness (pairwise characterization first — see below).
- `Dockerfile` is JVM-based; GraalVM native-image is a possible fourth column later.

Gate: no parity claim on sustained-concurrency runs until arbitrated against the Go
differential stub (the JDK HTTP-client empty-response edge above is a known
port-introduced difference under concurrency).

## Test-driven main-code accommodations (reachability audit)

Three production files changed shape for tests; all preserve the oracle's
production-visible surface (Spring Data's `JpaRepository` already exposed
`deleteAllInBatch`/`findAllById`/`findById` to production code — nothing new is
reachable that wasn't before):

- `deleteAllInBatch` on all six repositories — production-reachable (public repo
  methods) but called only from test cleanup. Same exposure as the oracle's
  inherited methods; not a new footgun.
- `findAllById` (Task) / `findById` (HierarchyNode) — production-reachable
  explicit `@Query` equivalents of the oracle's inherited methods. Same SQL
  Hibernate would emit for `find`; covered by integration tests.
- `findQueuedLeaves` moved to `TaskLockingQueries` (SessionFactory native) —
  **production-reachable**: called by `HierarchicalDispatchPlanner` on every
  hierarchical dispatch. Same SQL text, same params, same transaction
  (caller's); verified by `HierarchicalFairnessIntegrationTest` (4/4) rather
  than by construction. The locking readers moved the same way and are covered
  by the dispatch suites.

> NOTE — config placeholder defaults containing `://` are silently mangled.
> `${X:jdbc:postgresql://localhost:5432/equalix}` resolves to `5432/equalix`
> (not an error), producing a plausible-looking but invalid URL. Consequence:
> `EQUALIX_JDBC_URL` is required with no default. Any future config key that
> could carry a URL must follow the same pattern — required, no default,
> fail-fast at startup. Same silent-default class as Spring's ignored
> `spring.task.scheduling.enabled` flag: prefer loud failure over plausible
>   misconfiguration.

## Comparison integration notes

- **Endpoint path map** (per-side, for the harness — do not hardcode one side's):
  Spring `/actuator/health` + `/actuator/prometheus`; Micronaut `/health` +
  `/prometheus`; Go `/healthz` (+ `/readyz`) + `/metrics` (configurable via
  `EQUALIX_METRICS_PATH`).
- **Env templates are per-side.** Spring reads `SPRING_DATASOURCE_URL/_USERNAME/
  _PASSWORD` + `EQUALIX_API_KEY`; Go reads `EQUALIX_DSN` + `EQUALIX_API_KEY` (+…);
  Micronaut reads `EQUALIX_JDBC_URL` + `EQUALIX_DB_USER/_PASSWORD` +
  `EQUALIX_API_KEY`. The `EQUALIX_` overlap is convenient but not a shared
  contract — the harness normalizes per side.
- **Multi-instance is named out for Micronaut** (no ShedLock coordination).
  Single-instance rows are unaffected; any cross-instance comparison row excludes
  this column until locking lands. Conversely, do not measure the oracle's
  multi-instance scaling against this port — without coordination the Micronaut
  side would double-run every scheduler while the oracle coordinates.
