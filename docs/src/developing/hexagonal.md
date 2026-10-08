# Hexagonal architecture

The port preserves the oracle's hexagonal split:

- **Domain core** (`domain/`) — pure Java, no Micronaut or Spring imports. Model,
  ports, services. This is where the scheduler lives and why semantics are
  identical by construction.
- **Inbound adapters** (`adapter/in/`) — REST controllers, Kafka consumer,
  scheduled jobs, startup listener. They translate infrastructure into port
  calls.
- **Outbound adapters** (`adapter/out/`) — PostgreSQL (Micronaut Data JPA),
  Redis (Lettuce), HTTP executor, Micrometer metrics. They implement the output
  ports.
- **Config** (`config/`) — properties classes and factories that wire the beans.

Dependencies point inward: adapters depend on ports, never the reverse. The
domain has zero framework imports — verified by the build (the domain module
compiles without Micronaut on the classpath in CI; see the workflow file).
