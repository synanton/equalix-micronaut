# Contributing

Same discipline as the rest of the family:

- Conventional commits with phase scope (`feat(domain): ...`,
  `test(integration): ...`, `docs(EQLX-7): ...`).
- `EQLX-` branch prefix (shared across the family).
- Training gate (craft) and production gate (evidence) — every claim needs a
  citation in the evidence tree before it's marked done.
- DECISION / CORRECTION / NOTE taxonomy for spec §13 entries.
- Freshness marker bumps on phase transitions only.

## Git hygiene

Commit from status-verified-clean trees: run `git status` before staging and
stage explicit paths. Never `git add -A` without first confirming the status
shows only intended changes — unrelated working-tree state (other repos'
artifacts, scratch files, stale index entries) must stay out of the commit.

## Read before you write

[`docs/PORTING.md`](../../docs/PORTING.md) — framework mapping, test reachability
audit, and the silent-default traps (placeholder mangling, nested config
patterns, `@Introspected` requirements, merge-vs-persist semantics).
