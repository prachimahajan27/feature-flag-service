# Write-up: Feature Flag Service

## 1. What did I ask the AI to do, and what did I write or decide myself?

I used AI to scaffold the CRUD layer (entity, repository, service, controller),
the /eval endpoint, and the initial test suite, then to help debug dependency
and configuration issues as they came up. I made the core architectural
decisions myself: tenant isolation via an X-Tenant-ID header, scoping every
repository query by tenant (findByIdAndTenantId, findByNameAndTenantId) rather
than fetching by ID and checking ownership afterward, and having /eval return
"off" for unknown flags rather than a 404 — so a request never reveals whether
a flag exists for a different tenant.

## 2. Where did I override, correct, or throw away the AI's output — and why?

- The AI initially placed /eval under /flags/eval; I corrected it to a
  standalone /eval controller to match the spec's exact path.
- The first generated test suite shared H2 state across test methods,
  causing 409 Conflicts from duplicate flag names. Fixed with @Transactional
  on the test class so each test rolls back independently.
- delete() threw TransactionRequiredException because the repository's
  derived delete method had no active transaction — fixed at the service
  layer with @Transactional, which matters in production, not just tests.
- Debugged a Spring Boot 4 starter-modularization issue myself: the AI's
  initial pom.xml mixed the deprecated spring-boot-starter-web with its
  Boot 4 replacement (-webmvc), and @AutoConfigureMockMvc failed to resolve
  because Boot 4 split MockMvc's autoconfiguration into its own
  spring-boot-starter-webmvc-test module. I researched the actual cause
  rather than accepting a guessed fix.
- I prototyped an on/off/default flag-state enum (to more literally match
  the spec's "on/off/default" wording) but rolled it back — the added
  surface area wasn't worth the time trade-off given the box, and the
  current boolean model already satisfies the eval requirement cleanly.

## 3. Biggest trade-offs

- **In-memory H2 vs. Postgres**: chose H2 for zero-setup speed given the
  time box; a real deployment would need a persistent store.
- **Header-based tenancy vs. path-based (/tenants/{id}/flags)**: chose
  header-based for simplicity; path-based is more RESTful and visible in
  logs, but adds routing complexity.
- **Fail-safe "off" on unknown flags vs. explicit 404**: prioritized not
  leaking cross-tenant flag existence over precise error semantics for
  callers debugging typos.

## 4. What's missing, or what I'd do with another day

- No auth — X-Tenant-ID is trusted as-is; a real service would derive
  tenant from an authenticated token, not a client-supplied header.
- Targeting is currently limited to explicit user IDs; with more time I would
  add a structured attribute-rule model (for example, country or plan) and
  validate its operators and values.
- Optimistic locking is version-based and works for updates; I would extend
  the same client-visible concurrency contract to deletes if the API grows.
- Would revisit the on/off/default state model with more time, since the
  spec explicitly calls it out.
- No caching layer on /eval, which would matter under real load.
