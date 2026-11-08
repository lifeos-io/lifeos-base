# lifeos-base

**Technical** code shared by the services. Rule: no domain code (goals, decisions, recommendations…) goes here;
that code stays in the service that owns it. The bigger base grows, the more coupled the services become.

## Modules

One repo, one version: all modules are released together from a `release/X.Y.Z` branch and published to
`https://maven.pkg.github.com/lifeos-io/lifeos-base`. Each consumer depends only on the modules its runtime can use.

| Module | Runtime | Contents | Used by |
| --- | --- | --- | --- |
| `lifeos-base-core` | none (no Spring, enforced at build time) | `UuidV7`, `LifeOsHeaders`, `CorrelationIds`, `ErrorCode` / `CommonErrorCode` / `LifeOsException` | gateway + all services |
| `lifeos-base-web` | servlet | JWT resource-server config, `@CurrentUser`, RFC 9457 problem responses, correlation-ID filter, `Clock` | servlet services |
| `lifeos-base-messaging` | Kafka + JPA (planned) | event envelope, outbox publisher, inbox de-duplication | services that produce/consume events |
| `lifeos-base-test` | test scope (planned) | Testcontainers (PostgreSQL, Kafka), test JWTs | all services' tests |

The gateway is WebFlux: it must never depend on `lifeos-base-web` or `lifeos-base-messaging`, otherwise Spring
Boot finds servlet classes on the classpath and starts it as a servlet application.

## Core

- **`UuidV7`** — time-ordered UUIDs for `id` columns (fewer B-tree page splits than v4).
- **`LifeOsHeaders`** — `X-User-Id` (set by the gateway from JWT `sub`; informational only, services authorize
  from the JWT) and `X-Correlation-Id`.
- **`CorrelationIds`** — accepts a client value only if it is ≤ 64 chars of `[A-Za-z0-9._:-]`, otherwise
  generates a new one (prevents log forging / header injection); `MDC_KEY` for logging.
- **`ErrorCode`** — stable UPPER_SNAKE code + HTTP status + title. Cross-cutting codes in `CommonErrorCode`;
  each service adds its own enum with a service prefix (`STRATEGY_CAPACITY_EXCEEDED`).
- **`LifeOsException`** — an expected failure with an `ErrorCode`; its message is the client-facing `detail`,
  so it must not contain internals.

## Build

```bash
mvn -B verify
```

## Web (servlet services)

Add `lifeos-base-web` and the service gets, through Spring Boot auto-configuration (each bean backs off if the
service defines its own):

- **Security chain** — stateless JWT resource server; everything needs a token except `lifeos.security.public-paths`
  (default `/actuator/health/**`, `/actuator/info`, `/error`). Configure the JWT with Boot's standard properties:
  `spring.security.oauth2.resourceserver.jwt.issuer-uri` (Keycloak realm) and `...jwt.audiences`.
  401/403 keep the RFC 6750 `WWW-Authenticate` header and get a problem body.
- **`@CurrentUser UUID userId`** — the JWT `sub`; never the `X-User-Id` header. A mismatching header is only logged.
- **Problem responses (RFC 9457)** — every error has `type` (`urn:lifeos:error:<CODE>`), `title`, `status`,
  `detail`, `instance`, `code`, `correlationId`:
  `LifeOsException` → its code and details · bean validation → `VALIDATION_FAILED` with `errors[]` ·
  optimistic lock → `CONCURRENT_MODIFICATION` · Spring MVC errors (bad JSON, 404, 405…) → mapped code ·
  anything else → `INTERNAL_ERROR` with a generic detail (the real message only in the log).
  A service's own `@RestControllerAdvice` should use a higher precedence than `Ordered.LOWEST_PRECEDENCE - 20`.
- **Correlation ID filter** — first filter of the request; MDC key `correlationId`, echoed in `X-Correlation-Id`.
- **`Clock`** — `Clock.systemUTC()`; inject it instead of calling `Instant.now()` so tests can fix the time.
