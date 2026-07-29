# Feature Flag Service

A small multi-tenant feature-flag backend. Apps call `/eval` to decide
whether a feature is on for a given user; flags are fully isolated per tenant.

## Stack
Java 21, Spring Boot 4.1.0, Spring Data JPA, H2 (in-memory).

## Run it

```bash
./mvnw spring-boot:run
```

Service starts on `http://localhost:8080`.

## Run tests

```bash
./mvnw test
```

## Multi-tenancy

Every request must include an `X-Tenant-ID` header. All reads/writes are
scoped to that tenant at the repository query level (not just checked in
application code), so cross-tenant access isn't possible even if a
future endpoint forgets an explicit check.

## API

| Method | Path            | Description                          |
|--------|-----------------|---------------------------------------|
| POST   | `/flags`        | Create a flag                        |
| GET    | `/flags`        | List flags for the tenant            |
| GET    | `/flags/{id}`   | Get a single flag                    |
| PUT    | `/flags/{id}`   | Update a flag                        |
| DELETE | `/flags/{id}`   | Delete a flag                        |
| GET    | `/eval`         | Evaluate `?flag=X&user=Y` → on/off   |

All require header: `X-Tenant-ID: <tenant>`

### Flag fields

| Field              | Type          | Notes                                      |
|--------------------|---------------|---------------------------------------------|
| `name`             | string        | required, non-blank                        |
| `enabled`          | boolean       | master switch — off overrides everything   |
| `rolloutPercentage`| int (0–100)   | stable hash-based rollout                  |
| `targetedUsers`    | set of string | always ON for these users if flag enabled  |
| `version`          | long          | required on `PUT`, ignored on `POST` — see [Concurrency](#concurrency) |

## Evaluation logic

`GET /eval?flag=X&user=Y` resolves in this order:
1. Flag doesn't exist for this tenant → **off** (never leaks existence)
2. Flag disabled (`enabled=false`) → **off**
3. User is in `targetedUsers` → **on** (targeting always wins)
4. `rolloutPercentage >= 100` → **on**
5. Otherwise → deterministic SHA-256 hash of `flag+user` bucketed 0–99,
   compared against `rolloutPercentage`. Same user + same flag always
   gets the same answer — no randomness, no per-call flapping.
6. `rolloutPercentage == 0` and not targeted → **off** (safe default)

## Validation

Blank/missing flag name returns `400` (not the DB-constraint 500 it used
to). Handled via `@NotBlank` + a `@RestControllerAdvice` global exception
handler.

## Concurrency

`FeatureFlag` uses JPA optimistic locking (`@Version`). Two concurrent
updates to the same flag: the second writer gets a `409 Conflict`
instead of silently overwriting the first writer's change.

This is enforced at two levels, not just one:
- **API contract**: `PUT /flags/{id}` requires `version` in the request
  body — the version you last read the flag with. Omitting it returns
  `400`. Sending a stale version (one that's already been superseded by
  another writer) returns `409`.
- **DB-level backstop**: JPA's own `@Version` check on the entity, so
  even a client that races past the explicit check can't silently
  overwrite a concurrent change.

**Update flow for a client:**
1. `GET /flags/{id}` → note the returned `version`
2. `PUT /flags/{id}` with that `version` in the body
3. On `409`, re-fetch the flag (to get the current `version`) and retry
   with your intended change reapplied on top

## Design notes
- Unknown flag on `/eval` → returns `off` rather than an error, so a
  request never leaks whether a flag exists in another tenant.
- `deleteByIdAndTenantId` / `findByIdAndTenantId` used everywhere instead
  of ID-only lookups, so isolation is structural, not just checked.
- Rollout bucketing uses SHA-256 rather than `Object.hashCode()` or
  similar, to guarantee deterministic results across JVM restarts.

See `client-example.md` for a sample client integration.

See `WRITEUP.md` for the required one-page reflection.