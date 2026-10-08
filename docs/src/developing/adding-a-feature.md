# Adding a feature

1. **Start from the domain.** If the feature needs new state or a new decision,
   add it to `domain/model` and a `domain/service` first — no framework imports.
2. **Ports before adapters.** Define the port (`domain/port/in` or `out`), then
   implement it in the appropriate adapter. Never let an adapter call another
   adapter.
3. **Config follows the existing patterns.** Explicit `@Inject` constructors on
   beans (Lombok ctors are invisible to Micronaut's processor); explicit
   getters/setters + `@Introspected` on properties classes; nested config as
   static nested classes with relative `@ConfigurationProperties` paths.
4. **Tests at the right layer.** Domain logic in unit tests; adapter behavior in
   adapter tests; end-to-end in integration tests (Testcontainers, scheduling off).
5. **Update this book** when you change behavior operators or developers rely
   on — the book is the interface, the code is the implementation.

Read [`docs/PORTING.md`](../../docs/PORTING.md) first if the feature touches
transactions, scheduling, config, or the executor protocol — several
silent-default traps are documented there.
