# lifeos-base

**Technical** code shared by the services. Rule: no domain code (goals, decisions, recommendations…) goes here;
that code stays in the service that owns it. The bigger base grows, the more coupled the services become.

Currently:

- `LifeOsHeaders`: header names used between services (`X-User-Id`, `X-Correlation-Id`)
- `UuidV7`: time-ordered UUID generator (for the `id` columns)

Candidates for later: outbox/inbox infrastructure, event envelope publisher, ProblemDetail error format.
