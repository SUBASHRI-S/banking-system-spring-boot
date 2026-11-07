# Troubleshooting

Problems encountered while building and running the project, with cause and fix.

## Tests fail with `invalid value for parameter "TimeZone": "Asia/Calcutta"`
**Cause.** The PostgreSQL JDBC driver sends the JVM's default time zone; the `postgres:16` image rejects the legacy name `Asia/Calcutta`.
**Fix.** Force UTC: `-Duser.timezone=UTC` in the Surefire `argLine`, `TimeZone.setDefault(UTC)` in `AbstractIntegrationTest` and in `BankSimApplication.main`.
**Verified.** `mvn verify` passes on a machine in the Asia/Kolkata time zone.

## Mockito cannot call the real method of a spied Spring Data repository
**Symptom.** `doCallRealMethod` on a repository spy fails with an abstract-method error.
**Cause.** Spring Data repositories are proxies over interfaces, so there is no real method to call.
**Fix.** `LedgerIntegrityIT` now makes the transfer fail midway with a temporary PostgreSQL trigger that raises an exception for one specific ledger amount, then asserts that both balances and the ledger are unchanged. It is a more realistic failure than a mock.

## Testcontainers cannot reach Docker, or reports an old client version
Docker must be running. `pom.xml` pins `testcontainers.version` to 1.21.4 because the version managed by Spring Boot fails against Docker Engine 29.

## Application refuses to start and mentions `app.jwt.secret`
`JWT_SECRET` is missing or shorter than 32 characters. This is intentional: there is no default secret.

## Neon connection errors
Use the direct (non-pooled) connection in JDBC form with `sslmode=require`, and allow a few seconds for the compute to wake up.

## Dependabot pull requests fail CI
Pull requests that jump Spring Boot to 4.x or springdoc to 3.x need a coordinated migration and are not merged individually.
