# Codebase map

```
org.synanton.equalix
├── adapter
│   ├── in
│   │   ├── kafka/        TaskIngestionKafkaConsumer
│   │   ├── rest/         Controllers, DTOs, exception handlers
│   │   ├── schedule/     Dispatcher, calculators, watchdog, recovery jobs
│   │   └── startup/      CmsWarmUpListener
│   └── out
│       ├── cms/          CountMinSketch, Redis (Lettuce), hierarchical, tx-aware
│       ├── database/     Micronaut Data repos, entities, adapters
│       ├── executor/     HttpRemoteExecutorAdapter (JDK HttpClient)
│       └── metrics/      MicrometerPerformanceMonitorAdapter
├── config/              Properties, factories, API-key filter
└── domain
    ├── model/           Task, states, hierarchy, CMS statistics
    ├── port/            In/out ports (hexagonal boundaries)
    ├── service/         Dispatcher, calculator, RPS controller, watchdog, ...
    └── usecase/         Create/get/complete task
```

## Framework mapping (Spring oracle → Micronaut)

| Oracle | This port |
|---|---|
| `@Service` / `@Component` | `@Singleton` (+ explicit `@Inject` ctors) |
| `@Transactional` | `io.micronaut.transaction.annotation.Transactional` |
| `@ConfigurationProperties` | `@ConfigurationProperties` (+ `@Introspected` on nested POJOs) |
| `@Scheduled` + ShedLock | `@Scheduled` (**no distributed lock** — named out) |
| `WebClient` (Reactor) | JDK `HttpClient.sendAsync`, pinned HTTP/1.1 |
| `StringRedisTemplate` | Lettuce `StatefulRedisConnection` |
| Spring Kafka + `Acknowledgment` | `@KafkaListener`, offset commits after return |
| `TransactionSynchronizationManager` | Hibernate `ActionQueue` completion process |

The full mapping, framework findings, and reachability audit live in
[`docs/PORTING.md`](../../docs/PORTING.md) — read it before touching config or
transactions; several silent-default traps are documented there.
