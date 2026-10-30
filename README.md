# BankSim: Banking REST API

[![CI](https://github.com/SUBASHRI-S/banking-system-spring-boot/actions/workflows/ci.yml/badge.svg)](https://github.com/SUBASHRI-S/banking-system-spring-boot/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue)

A simulated online banking backend built with Spring Boot. It focuses on the hard parts of a money system: **atomic transfers, concurrency safety, and secure access to accounts**, and it proves them with tests that run against a real PostgreSQL database.

> Fictional data only. Currency INR. No real money and no real bank APIs.

## Highlights

- **Atomic transfers.** A transfer is one database transaction. If any step fails, neither balance changes.
- **Deadlock-free concurrency.** Both account rows are locked with `SELECT ... FOR UPDATE`, always the lower account id first. 50 simultaneous transfers in opposite directions finish with no deadlocks; the same run with naive locking produced 46 (see `scripts/verify-locking.sh`).
- **Ledger integrity.** Every money movement writes a ledger entry. An account's balance always equals the sum of its ledger entries, and is checked by a test.
- **Defence in depth.** `CHECK (balance >= 0)` in the database acts as a last safety net, even if application code has a bug.
- **JWT security.** Stateless authentication with Spring Security, BCrypt password hashing, roles (`USER`, `ADMIN`), and ownership checks on every account operation.
- **Idempotent transfers and daily limits.** The same `Idempotency-Key` creates only one transfer; outgoing transfers per account per day are capped.
- **One error format.** Every error returns the same JSON shape and never exposes a stack trace.

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Security 6 (OAuth2 Resource Server, JWT) · Spring Data JPA / Hibernate · PostgreSQL 16 · Flyway · springdoc OpenAPI (Swagger UI) · JUnit 5 · Mockito · Testcontainers · JaCoCo · Docker · GitHub Actions

## Quality

| Check | Result |
|---|---|
| Tests | 36 (unit, API, integration, concurrency) |
| Test run | `mvn verify` passes locally and in CI |
| Coverage | 91% instructions, 77% branches (JaCoCo, 30 Oct 2026) |
| Database | Tests run against real PostgreSQL 16 via Testcontainers, not H2 |

## Quick start

Requires Docker only.

```bash
git clone https://github.com/SUBASHRI-S/banking-system-spring-boot.git
cd banking-system-spring-boot
cp .env.example .env          # optional, compose has safe local defaults
docker compose up --build
```

| | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

To create an `ADMIN` user (needed for freeze and unfreeze), set `SEED_ADMIN_PASSWORD` in `.env` before starting. The admin email defaults to `admin@example.com`. No password is stored in Git.

### Try the API

1. `POST /api/v1/auth/register`, then `POST /api/v1/auth/login`.
2. Click **Authorize** in Swagger UI and paste the `accessToken`.
3. Open an account, deposit, and transfer.

Or run `./scripts/smoke-test.sh` (needs `curl` and `jq`), or import `postman/banksim.postman_collection.json` into Postman.

### Run without Docker

Needs Java 21, Maven 3.9+ and a PostgreSQL 16 database.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/bank
export DB_USERNAME=bank
export DB_PASSWORD=bank
export JWT_SECRET="$(openssl rand -base64 48)"   # at least 32 characters
mvn spring-boot:run
```

## API

Base path `/api/v1`.

| Method | Path | Access |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | Public |
| GET, POST | `/accounts` | User |
| GET | `/accounts/{id}` | User (owner) |
| POST | `/accounts/{id}/deposit`, `/accounts/{id}/withdraw` | User (owner) |
| POST | `/transfers` (optional `Idempotency-Key` header) | User (owner of source account) |
| GET | `/accounts/{id}/transactions` (`page`, `size`, `type`, `from`, `to`) | User (owner) |
| GET | `/admin/accounts` | Admin |
| PATCH | `/admin/accounts/{id}/status` | Admin |
| GET | `/actuator/health` | Public |

Errors share one shape: `type`, `title`, `status`, `detail`, `code`, `timestamp`, `path` (plus `errors` for field validation).

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_ERROR`, `SAME_ACCOUNT` | 400 | Invalid request |
| `UNAUTHORIZED` | 401 | Missing, invalid or expired token, or bad login |
| `FORBIDDEN` | 403 | Wrong role |
| `ACCOUNT_NOT_FOUND` | 404 | Missing, or not yours (existence is never revealed) |
| `DUPLICATE_EMAIL`, `ACCOUNT_FROZEN` | 409 | Conflict |
| `INSUFFICIENT_FUNDS`, `DAILY_LIMIT_EXCEEDED`, `IDEMPOTENCY_KEY_REUSED` | 422 | Rule violation |
| `INTERNAL_ERROR` | 500 | Unexpected error (details only in logs) |

## Architecture

```
Client (Swagger UI / Postman)
   -> Security filter chain (JWT validation)
   -> Controllers  (REST + validation)
   -> Services     (business rules + transactions)
   -> Repositories (Spring Data JPA)
   -> PostgreSQL   (schema managed by Flyway)
```

Controllers never touch repositories; services own all business rules and `@Transactional`. Code is organised by feature: `auth`, `user`, `account`, `transaction`, `transfer`, with shared code in `common` and `config`.

Full design, ER diagram, transfer sequence and decisions: [`docs/DESIGN.md`](docs/DESIGN.md). Requirement-to-code-to-test mapping: [`docs/TRACEABILITY.md`](docs/TRACEABILITY.md).

## Key design decisions

| Decision | Why |
|---|---|
| `NUMERIC(19,2)` and `BigDecimal` for money, never `double` | Exact decimal arithmetic |
| Pessimistic row locks, lowest id first | No lost updates, no deadlocks between opposite transfers |
| Flyway migrations instead of `ddl-auto` | Versioned, reproducible schema |
| Testcontainers instead of H2 | Locking and constraints behave differently in H2 |
| 404 instead of 403 for other users' accounts | Does not reveal whether an account exists |
| CSRF disabled | The API is stateless and uses a Bearer header, not cookies |

## Testing

```bash
mvn verify      # unit + integration tests + coverage report (Docker must be running)
```

Coverage report: `target/site/jacoco/index.html`.

| Area | Test classes |
|---|---|
| Business rules (no database) | `AccountServiceTest`, `TransferServiceTest`, `MoneyTest`, `LedgerSummaryTest` |
| HTTP behaviour through the security chain | `ApiFlowIT` |
| Ledger equals balance, rollback on failure, frozen accounts | `LedgerIntegrityIT` |
| 50 parallel transfers, opposite-direction transfers without deadlock | `ConcurrentTransferIT` |
| Idempotency key and daily limit | `AdvancedTransferIT` |

## Configuration

| Variable | Default | Secret |
|---|---|---|
| `JWT_SECRET` | none (required, 32+ characters) | yes |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bank` | no |
| `DB_USERNAME`, `DB_PASSWORD` | `bank` / `bank` (local only) | yes |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | `admin@example.com` / empty (no admin) | password: yes |
| `DAILY_TRANSFER_LIMIT` | `100000.00` | no |
| `PORT` | `8080` | no |

Never commit `.env`. Real values belong only in the hosting dashboard.

## Deployment

The repository includes a `Dockerfile` and a `render.yaml` Blueprint for [Render](https://render.com), paired with a free [Neon](https://neon.com) PostgreSQL database.

1. Create a Neon database and use the **direct** (non-pooled) connection. Convert it to JDBC form: `jdbc:postgresql://<host>/<database>?sslmode=require`, with the username and password as separate values.
2. On Render choose **New > Blueprint** and select this repository.
3. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and, optionally, `SEED_ADMIN_PASSWORD`. `JWT_SECRET` is generated automatically.
4. Check `https://<your-service>.onrender.com/actuator/health`.

On free hosting the service sleeps after about 15 minutes idle, so the first request after a pause can take around a minute.

## Limitations

- Simulated only; no real money, no real compliance.
- Single instance; no caching or messaging.
- No refresh tokens and no rate limiting.
- Free hosting sleeps when idle.
- Spring Boot 3.5 no longer receives free patch releases; migrating to Boot 4 is a possible next step.

## Repository layout

```
src/main/java/com/banksim/   application code (config, common, auth, user, account, transaction, transfer)
src/main/resources/db/       Flyway migrations V1 to V4
src/test/java/com/banksim/   unit and Testcontainers tests
docs/                        design, traceability, troubleshooting
postman/                     Postman collection (success and failure cases)
scripts/                     smoke test and locking verification
```
