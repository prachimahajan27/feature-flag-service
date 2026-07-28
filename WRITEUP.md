# Write-up: Feature Flag Service

## 1. What did I ask the AI to do, and what did I decide myself?

I used AI to scaffold the CRUD layer (entity, repository, service, controller),
the /eval endpoint, and the initial test suite. I made the core architectural
decisions myself: tenant isolation via X-Tenant-ID header, scoping every
repository query by tenant (findByIdAndTenantId, findByNameAndTenantId) rather
than checking tenant ownership after an ID-only fetch, and the choice to have
/eval return "off" for unknown flags rather than a 404 — to avoid leaking
whether a flag exists for another tenant.

## 2. Where did I override, correct, or throw away the AI's output — and why?

- The AI initially placed /eval under /flags/eval; I corrected it to a
  standalone controller at /eval to match the spec's exact path.
- The generated tests initially shared H2 state across test methods, causing
  409 Conflicts from duplicate flag names. I added @Transactional to the
  test class so each test rolls back independently.
- The delete endpoint threw TransactionRequiredException because the
  repository's derived delete method had no active transaction. I added
  @Transactional at the service layer — a fix that matters in production,
  not just in tests.

## 3. Biggest trade-offs

- **In-memory H2 vs. Postgres**: chose H2 for zero-setup speed given the
  time box; a real deployment would need a persistent store and probably
  a unique index migration strategy.
- **Header-based tenancy vs. path-based (/tenants/{id}/flags)**: chose
  header-based for simplicity and to keep URLs clean; path-based would be
  more RESTful and visible in logs/URLs, but adds routing complexity.
- **Fail-safe "off" on unknown flags vs. explicit 404**: prioritized not
  leaking cross-tenant flag existence over precise error semantics for
  callers debugging typos.

## 4. What's missing / what I'd do with another day

- No auth — X-Tenant-ID is trusted as-is; a real service would need to
  authenticate the caller and derive tenant from a token, not a
  client-supplied header.
- No percentage-based rollout or user-targeting rules, just global on/off.
- No caching layer on /eval, which would matter under real load.
- Would add optimistic locking on updates to handle concurrent writes.git