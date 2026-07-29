# Write-up: Feature Flag Service

## 1. What did I ask the AI to do, and what did I write or decide myself?

I used AI to scaffold the CRUD layer, the /eval endpoint, the test suite,
and to help debug dependency/configuration issues as they came up. I made
the core architectural decisions myself: tenant isolation via an
X-Tenant-ID header with every repository query scoped by tenant, /eval
returning "off" for unknown flags rather than a 404 (so a request never
reveals whether a flag exists for a different tenant), and — after
reviewer feedback — the precedence order for evaluation (targeting beats
rollout beats the disabled/default off-path).

## 2. Where did I override, correct, or throw away the AI's output — and why?

- The AI initially placed /eval under /flags/eval; I corrected it to a
  standalone /eval controller to match the spec's exact path.
- Early tests shared H2 state across methods, causing spurious 409s.
  Fixed with @Transactional on the test class.
- delete() threw TransactionRequiredException — the derived delete method
  needed an explicit @Transactional at the service layer, not just in
  tests, since this would have broken in production too.
- Debugged a Spring Boot 4 starter-modularization issue myself: pom.xml
  mixed the deprecated spring-boot-starter-web with its Boot 4 replacement
  (-webmvc), and @AutoConfigureMockMvc failed because Boot 4 split MockMvc
  autoconfiguration into its own spring-boot-starter-webmvc-test module.
- My first optimistic-locking test passed for the wrong reason: both
  repository reads inside one @Transactional test method returned the
  *same* cached entity instance (Hibernate's first-level cache), so
  editing one silently edited both and no real conflict occurred. Fixed
  by explicitly detaching each copy via EntityManager before mutating it,
  so the test exercises an actual stale-version conflict.
- I prototyped an on/off/default flag-state enum early on but rolled it
  back — the reviewer feedback made clear the missing piece was
  percentage-based evaluation logic, not a third boolean-like state, so
  I redirected effort there instead.

## 3. Biggest trade-offs

- **In-memory H2 vs. Postgres**: chose H2 for zero-setup speed given the
  time box; a real deployment would need a persistent store.
- **Header-based tenancy vs. path-based (/tenants/{id}/flags)**: chose
  header-based for simplicity; path-based is more RESTful and visible in
  logs, but adds routing complexity.
- **SHA-256 hash bucketing vs. a simpler hash**: chose SHA-256 for
  deterministic, well-distributed buckets across JVM restarts, at the
  cost of a small amount of extra CPU per eval call — negligible for
  this scale, but worth revisiting under very high QPS.
- **Fail-safe "off" on unknown/disabled flags vs. explicit error**:
  prioritized never leaking cross-tenant flag existence or state over
  precise error semantics for callers debugging typos.

## 4. What's missing, or what I'd do with another day

- No auth — X-Tenant-ID is trusted as-is; a real service would derive
  tenant from an authenticated token, not a client-supplied header.
- No attribute-based targeting beyond user ID (e.g. targeting by region,
  plan tier) — only explicit user-ID lists right now.
- No caching layer on /eval, which would matter under real load.
- Optimistic-lock conflicts currently surface as a flat 409 with no
  retry guidance; a real client library would want a documented retry
  pattern (refetch-and-reapply).