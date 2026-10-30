# banking-system-spring-boot

Simulated banking REST API: JWT security, atomic and concurrency-safe transfers, PostgreSQL, Docker, CI.
**Fictional data only. No real money, no real bank APIs.**

Stack: Java 21, Spring Boot 3.5, Spring Security 6 (JWT, OAuth2 Resource Server), Spring Data JPA, PostgreSQL 16, Flyway, springdoc (Swagger UI), JUnit 5, Mockito, Testcontainers, JaCoCo, Docker, GitHub Actions.

The full design (requirements, ER diagram, transfer sequence, decisions D1 to D7) is in [`docs/DESIGN.md`](docs/DESIGN.md).

## Verification status (read this first)

This project was written from the design document in an environment with no access to Maven Central and no Docker daemon, so **it has not been compiled or run yet**. Treat everything as unverified until you have seen it pass yourself:

- [ ] `mvn verify` is green on my machine
- [ ] `docker compose up --build` starts and `/actuator/health` returns `UP`
- [ ] `scripts/smoke-test.sh` runs clean against the local instance
- [ ] CI is green on `main`
- [ ] Deployed demo answers on its public URL

Checked so far (29 Oct 2026, PostgreSQL 16, no Java build possible in that environment):
- Flyway migrations V1 to V4 apply cleanly on a fresh PostgreSQL 16 database.
- `scripts/verify-locking.sh`: with lowest-id-first locking, 50 simultaneous A/B and B/A transfers gave 0 deadlocks and unchanged balances; with naive source-first locking the same run gave 46 deadlock errors; `CHECK (balance >= 0)` rejected a negative balance.
- All Java sources parse with `javac` (no syntax errors). Dependency resolution, compilation and the Spring tests were NOT run.

Coverage:  After `mvn verify`, open `target/site/jacoco/index.html` and write down the real number here: '91 %'`.

## Quick start (Docker, nothing else to install)

```bash
git clone <your-repo-url> && cd banking-system-spring-boot
cp .env.example .env            # optional; compose has fake local defaults
docker compose up --build
```

- Swagger UI: <http://localhost:8080/swagger-ui.html> (also reachable from `/`)
- Health: <http://localhost:8080/actuator/health>

To get an ADMIN user (needed to try freeze/unfreeze) set `SEED_ADMIN_PASSWORD` in `.env` before starting. The admin email defaults to `admin@example.com`. If the variable is empty, no admin is created. No password is stored in Git.

Try it without any tool:

```bash
./scripts/smoke-test.sh          # needs curl and jq
```

or import `postman/banksim.postman_collection.json` into Postman and run the folders in order.

## Run without Docker

Needs Java 21, Maven 3.9+ and a PostgreSQL 16 database.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/bank
export DB_USERNAME=bank
export DB_PASSWORD=bank
export JWT_SECRET="$(openssl rand -base64 48)"     # at least 32 characters, or the app refuses to start
mvn spring-boot:run
```

Optional: generate the Maven wrapper once with `mvn -N wrapper:wrapper`, then use `./mvnw` instead of `mvn`.

## Tests

```bash
mvn verify          # unit tests + Testcontainers integration tests + JaCoCo report
```

Integration tests start a real PostgreSQL 16 in Docker, so **Docker must be running**. Reports: `target/surefire-reports`, coverage: `target/site/jacoco/index.html`.

| Test ID | Class | What it proves |
|---|---|---|
| T-UNIT-01..04 | `AccountServiceTest`, `TransferServiceTest`, `MoneyTest` | Business rules without a database |
| T-API-01..06 | `ApiFlowIT` | HTTP behaviour through the real security chain |
| T-INT-01..03 | `LedgerIntegrityIT` | Ledger equals balance, rollback on failure, frozen accounts |
| T-CON-01..02 | `ConcurrentTransferIT` | 50 parallel transfers; A to B and B to A at once, no deadlock |
| T-ADV-01..02 | `AdvancedTransferIT` | Idempotency key, daily limit |

Requirement to code to test mapping: [`docs/TRACEABILITY.md`](docs/TRACEABILITY.md).

## API (base path `/api/v1`)

| Method | Path | Auth |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | none |
| GET / POST | `/accounts` | USER |
| GET | `/accounts/{id}` | USER (owner) |
| POST | `/accounts/{id}/deposit`, `/accounts/{id}/withdraw` | USER (owner) |
| POST | `/transfers` (optional `Idempotency-Key` header) | USER (owner of source) |
| GET | `/accounts/{id}/transactions?page=&size=&type=&from=&to=` | USER (owner) |
| GET | `/admin/accounts` | ADMIN |
| PATCH | `/admin/accounts/{id}/status` | ADMIN |
| GET | `/actuator/health` | none |

Every error has one shape: `type`, `title`, `status`, `detail`, `code`, `timestamp`, `path` (plus `errors` for field validation).
Codes: `VALIDATION_ERROR` 400, `SAME_ACCOUNT` 400, `UNAUTHORIZED` 401, `FORBIDDEN` 403, `ACCOUNT_NOT_FOUND` 404, `DUPLICATE_EMAIL` 409, `ACCOUNT_FROZEN` 409, `INSUFFICIENT_FUNDS` 422, `DAILY_LIMIT_EXCEEDED` 422, `IDEMPOTENCY_KEY_REUSED` 422, `INTERNAL_ERROR` 500.

## Configuration (environment variables)

| Name | Default | Secret? |
|---|---|---|
| `JWT_SECRET` | none, **required**, 32+ chars | yes |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bank` | no |
| `DB_USERNAME` / `DB_PASSWORD` | `bank` / `bank` (local only) | yes |
| `SPRING_PROFILES_ACTIVE` | none (`prod` on the host) | no |
| `PORT` | `8080` | no |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` | `admin@example.com` / empty (no admin) | password: yes |
| `DAILY_TRANSFER_LIMIT` | `100000.00` | no |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` | no |

Never commit `.env`. Real values live only in the host's dashboard.

## Deploy for free: Render + Neon

Free-tier terms change; re-check [Render](https://render.com/docs/free) and [Neon](https://neon.com/docs/introduction/plans) before you start.

1. **Database (Neon):** create a free project and database. Use the **direct** (non-pooled) connection details. Convert them to JDBC form:
   `DB_URL=jdbc:postgresql://<host>/<database>?sslmode=require`, with `DB_USERNAME` and `DB_PASSWORD` as separate values (JDBC does not accept the `postgresql://user:pass@host` form).
2. **App (Render):** push this repo to GitHub, then Render > New > Blueprint > select the repo. `render.yaml` creates a Docker web service on the free plan with health check `/actuator/health`.
3. In the Render dashboard set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. `JWT_SECRET` is generated for you. Optionally set `SEED_ADMIN_PASSWORD`.
4. Deploy, then run `./scripts/smoke-test.sh https://<your-service>.onrender.com`.

Honest consequences (put the live link and this note on your resume/README):
- The free web service sleeps after about 15 minutes idle. **The first request can take about 60 seconds.**
- Neon free compute also sleeps when idle, so the first database call after a pause is slower (the prod profile allows 60 s for it).
- Whether the JVM fits in the free instance's memory is **unverified**. Test it. If it runs out of memory, lower `MaxRAMPercentage`, add `-XX:TieredStopAtLevel=1`, or fall back to `docker compose` for demos.

## Design notes worth knowing for interviews

- **Money:** `NUMERIC(19,2)` in the database, `BigDecimal` in Java, never `double`. At most 2 decimals, enforced in `Money.validate`.
- **Atomic transfer:** one `@Transactional` method (`TransferService`). A failure after the balances change rolls everything back (T-INT-02).
- **No lost updates / no deadlock:** `SELECT ... FOR UPDATE` on both rows, lowest account id first. The rows are locked before they are loaded into the persistence context, so balances are never stale (this is why the earlier checks use scalar `exists`/id queries).
- **Last safety net:** `CHECK (balance >= 0)` in the database.
- **404 not 403** for other people's accounts, so their existence is not leaked.
- **CSRF disabled** because the API is stateless and uses a Bearer header, not cookies.
- **Idempotency:** same `Idempotency-Key` returns the first result; duplicates queue on the source row lock, so even simultaneous duplicates create one transfer.

## Known limitations

- Simulated only; no real money, no real compliance.
- Single instance; no caching or messaging.
- Free hosting sleeps when idle.
- No refresh tokens and no rate limiting.
- Optional static dashboard (milestone M9) and the Spring Boot 4.1 migration (M10) are not included. Swagger UI and Postman are the front end.
- Spring Boot 3.5 no longer gets free patches (see D1 in the design doc). Use the latest 3.5.x patch, and consider M10.

## Repo layout

```
src/main/java/com/banksim/{config,common,auth,user,account,transaction,transfer}
src/main/resources/db/migration/V1..V4   Flyway migrations
src/test/java/com/banksim                unit + Testcontainers tests
docs/                                    DESIGN, TRACEABILITY, TROUBLESHOOTING
postman/                                 Postman collection (success and failure cases)
scripts/smoke-test.sh                    curl end-to-end check
Dockerfile, docker-compose.yml, render.yaml, .github/workflows/ci.yml
```
