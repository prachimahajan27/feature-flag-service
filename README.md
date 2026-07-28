# Feature Flag Service

A small multi-tenant feature-flag backend. Apps call `/eval` to decide
whether a feature is on for a given user; flags are fully isolated per tenant.

## Stack
Java 17, Spring Boot 3.3, Spring Data JPA, H2 (in-memory).

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

## Design notes
- Unknown flag on `/eval` → returns `off` rather than an error, so a
  request never leaks whether a flag exists in another tenant.
- `deleteByIdAndTenantId` / `findByIdAndTenantId` used everywhere instead
  of ID-only lookups, so isolation is structural, not just checked.

See `client-example.md` for a sample client integration.

See `WRITEUP.md` for the required one-page reflection.