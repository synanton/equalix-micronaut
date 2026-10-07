# Porting notes — Spring Boot oracle → Micronaut

Scope: full-parity port of `equalix` (Spring Boot 3.5, Java 21) to Micronaut 4.x.
Package `org.synanton.equalix` kept identical so files diff cleanly against the oracle.
Migrations under `src/main/resources/db/migration/` are byte-identical to the oracle —
schema divergence would invalidate every comparison.

Verified so far: `mvn test` 235/235 green — the 179 oracle unit tests that don't need Spring/Testcontainers (copied verbatim),
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
| `WebClient` (Reactor, fire-and-forget) | JDK `HttpClient.sendAsync` | Same wire bytes, same non-blocking semantics, zero framework coupling |
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

## Intentional deviations from the oracle

- **Env vars renamed to neutral `EQUALIX_*`** (`EQUALIX_JDBC_URL`, `EQUALIX_DB_USER`,
  `EQUALIX_DB_PASSWORD`, `EQUALIX_API_KEY`, `KAFKA_BOOTSTRAP_SERVERS` kept). Same
  defaults as the oracle; see `docker-compose.yml`.
- **No ShedLock** (see table). The V2 `shedlock` table migration is still applied.
- **No KafkaHEADS UP in default compose** — app runs without a broker; set
  `KAFKA_BOOTSTRAP_SERVERS` to wire one.
- Management paths are Micronaut's (`/health`, `/info`, `/prometheus`) rather than
  `/actuator/*`; only `/health`, `/info` are public, same policy as the oracle.

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

## Not yet ported (for the comparison phase)

- Differential harness / benchmarks: characterization first (pairwise), not an
  N-way differential — see below.
- `Dockerfile` is JVM-based; GraalVM native-image is a possible fourth column later.

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
  this column until locking lands.
