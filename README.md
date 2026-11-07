# BankSim: Secure Banking Backend

[![CI](https://github.com/SUBASHRI-S/banking-system-spring-boot/actions/workflows/ci.yml/badge.svg)](https://github.com/SUBASHRI-S/banking-system-spring-boot/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue)

A REST backend for a simulated retail bank, built with Java 21, Spring Boot and PostgreSQL. It focuses on the problems that decide whether a money system can be trusted: **transfers that are atomic and safe under concurrency, a tamper-evident audit trail, and an independent check that balances still match the ledger.**

> Fictional data only. Currency INR. No real money and no real bank APIs.

## Business problem and how it is addressed

| Risk in a banking system | Business impact | Control in BankSim |
|---|---|---|
| Two requests spend the same balance | Direct financial loss | Row-level locks (`SELECT ... FOR UPDATE`) inside one transaction |
| Opposite transfers block each other | Stuck requests, timeouts | Locks always taken lowest account id first: 0 deadlocks in the 50-transfer test, against 46 with naive ordering |
| Transfer fails halfway | Money debited but not credited | Single database transaction; both sides commit or neither does |
| Client retries a timed-out payment | Duplicate payment | `Idempotency-Key` header: the same key produces one transfer |
| Bug or manual change corrupts a balance | Silent drift, audit findings | Nightly reconciliation recomputes every balance from the ledger and flags differences |
| Disputes and insider risk | No proof of who did what | Append-only audit log (database trigger blocks update and delete), including rejected attempts |
| Credential guessing | Account takeover | BCrypt hashing, login throttling (HTTP 429), constant-message login errors |
| Customers probing for other accounts | Data exposure | Ownership checks in the service layer; other users' accounts return 404 |
| Hard to diagnose production issues | Slow incident response | `X-Request-Id` correlation, Prometheus metrics for transfer outcomes and latency |

## Features

- **Accounts and money movement:** register, login, open savings or current accounts, deposit, withdraw, transfer by account number, paginated and filterable history.
- **Safety:** `NUMERIC(19,2)` and `BigDecimal` for money, a database `CHECK (balance >= 0)` as a last safety net, a ledger entry for every movement, daily transfer limit.
- **Security:** Spring Security 6 with stateless JWT (OAuth2 Resource Server), `USER` and `ADMIN` roles, admin-only operational endpoints.
- **Operations:** audit trail, reconciliation, metrics, request correlation, health endpoint, one consistent error format that never exposes stack traces.
- **Engineering practice:** Flyway migrations, real-PostgreSQL integration tests, architecture rules enforced by tests, Docker, CI on every push.

## Quality

| Check | Result |
|---|---|
| Automated tests | 57, all passing (unit, API, integration, concurrency, architecture) |
| Coverage | 90% instructions, 74% branches (JaCoCo, 7 Nov 2026); money-critical `transfer` package 98% |
| Database in tests | Real PostgreSQL 16 through Testcontainers, not H2 |
| CI | GitHub Actions runs `mvn verify` on every push and pull request |

## Technology

Java 21 · Spring Boot 3.5 · Spring Security 6 · Spring Data JPA / Hibernate · Spring AOP · PostgreSQL 16 · Flyway · Micrometer / Prometheus · springdoc OpenAPI · JUnit 5 · Mockito · Testcontainers · ArchUnit · JaCoCo · Docker · GitHub Actions

## Run it

Requires Docker only.

```bash
git clone https://github.com/SUBASHRI-S/banking-system-spring-boot.git
cd banking-system-spring-boot
cp .env.example .env     # set SEED_ADMIN_PASSWORD to create an admin user
docker compose up --build
```

| | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

**Try it:** call `POST /api/v1/auth/register` and `POST /api/v1/auth/login`, click **Authorize** in Swagger UI with the returned `accessToken`, open two accounts, deposit and transfer. As admin you can also call `GET /api/v1/admin/audit-events` and `GET /api/v1/admin/reconciliation`. A scripted walkthrough is in `scripts/smoke-test.sh` (needs `curl` and `jq`), and `postman/banksim.postman_collection.json` contains success and failure cases.

<details>
<summary>Run without Docker</summary>

Needs Java 21, Maven 3.9+ and PostgreSQL 16.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/bank
export DB_USERNAME=bank
export DB_PASSWORD=bank
export JWT_SECRET="$(openssl rand -base64 48)"   # at least 32 characters
mvn spring-boot:run
```
</details>

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
| GET | `/admin/audit-events` (`type`, `page`, `size`) | Admin |
| GET | `/admin/reconciliation` | Admin |
| GET | `/actuator/health` | Public |
| GET | `/actuator/prometheus`, `/actuator/metrics` | Admin |

Every error has the same shape: `type`, `title`, `status`, `detail`, `code`, `timestamp`, `path`, plus `requestId` and `errors` (field validation) when applicable.

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_ERROR`, `SAME_ACCOUNT` | 400 | Invalid request |
| `UNAUTHORIZED` | 401 | Missing, invalid or expired token, or bad login |
| `FORBIDDEN` | 403 | Wrong role |
| `ACCOUNT_NOT_FOUND` | 404 | Missing, or not owned by the caller |
| `DUPLICATE_EMAIL`, `ACCOUNT_FROZEN` | 409 | Conflict |
| `INSUFFICIENT_FUNDS`, `DAILY_LIMIT_EXCEEDED`, `IDEMPOTENCY_KEY_REUSED` | 422 | Business rule violation |
| `TOO_MANY_ATTEMPTS` | 429 | Too many failed logins for this email |
| `INTERNAL_ERROR` | 500 | Unexpected error (details only in logs) |

## Architecture

```
Client -> Security filter chain (JWT) -> Controllers -> Services (@Transactional) -> Repositories -> PostgreSQL (Flyway)
                                              \-> Audit and metrics aspects (AOP)
```

Controllers never access repositories; services own business rules and transaction boundaries. Code is organised by feature: `auth`, `user`, `account`, `transaction`, `transfer`, `audit`, `reconciliation`, `observability`, with shared code in `common` and `config`. These layering rules are enforced by `ArchitectureTest`.

| Decision | Reason |
|---|---|
| Pessimistic locking, lowest id first | Money movement is a hot, correctness-critical path; waiting is better than retrying ([ADR 0001](docs/adr/0001-pessimistic-locking.md)) |
| Audit written by an aspect in its own transaction | A rejected transfer rolls back, but its audit record must survive ([ADR 0002](docs/adr/0002-audit-by-aspect.md)) |
| Reconciliation as an independent control | Tests prove the code; reconciliation checks the production data ([ADR 0003](docs/adr/0003-reconciliation.md)) |
| In-memory login throttling | Simple and effective for one instance; shared store needed to scale ([ADR 0004](docs/adr/0004-login-throttling.md)) |
| Request ids and business metrics | Faster incident diagnosis ([ADR 0005](docs/adr/0005-observability.md)) |
| Flyway instead of `ddl-auto` | Versioned, reproducible schema |
| Testcontainers instead of H2 | Locking and constraint behaviour differ |
| CSRF disabled | Stateless API with a Bearer header and no cookies |

More detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (diagrams and control map), [`docs/DESIGN.md`](docs/DESIGN.md) (data model, transfer flow, API specification), [`docs/TRACEABILITY.md`](docs/TRACEABILITY.md) (requirement to code to test), [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).

## Testing

```bash
mvn verify      # all tests plus coverage report (Docker must be running)
```

Coverage report: `target/site/jacoco/index.html`.

| Area | Test classes |
|---|---|
| Business rules (no database) | `AccountServiceTest`, `TransferServiceTest`, `MoneyTest`, `LedgerSummaryTest`, `LoginAttemptServiceTest` |
| HTTP behaviour through the security chain | `ApiFlowIT`, `LoginThrottleIT` |
| Ledger equals balance, rollback on failure, frozen accounts | `LedgerIntegrityIT` |
| 50 parallel transfers, opposite directions without deadlock | `ConcurrentTransferIT` |
| Idempotency key and daily limit | `AdvancedTransferIT` |
| Audit trail and immutability | `AuditTrailIT` |
| Reconciliation detects tampering | `ReconciliationIT` |
| Metrics and request ids | `ObservabilityIT` |
| Layering rules | `ArchitectureTest` |

`scripts/verify-locking.sh` reproduces the deadlock comparison against a local PostgreSQL.

## Configuration

| Variable | Default | Secret |
|---|---|---|
| `JWT_SECRET` | none (required, 32+ characters) | yes |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bank` | no |
| `DB_USERNAME`, `DB_PASSWORD` | `bank` / `bank` (local only) | yes |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | `admin@example.com` / empty (no admin) | password: yes |
| `DAILY_TRANSFER_LIMIT` | `100000.00` | no |
| `MAX_LOGIN_FAILURES`, `LOCKOUT_SECONDS` | `5` / `900` | no |
| `RECONCILIATION_CRON` | `0 0 2 * * *` (UTC) | no |
| `PORT` | `8080` | no |

Real values belong only in the hosting dashboard; `.env` is never committed.

## Deployment

The repository contains a `Dockerfile` and a `render.yaml` Blueprint for [Render](https://render.com) with a [Neon](https://neon.com) PostgreSQL database.

1. Create a Neon database and use the direct (non-pooled) connection in JDBC form: `jdbc:postgresql://<host>/<database>?sslmode=require`.
2. In Render choose **New > Blueprint** and select this repository.
3. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and optionally `SEED_ADMIN_PASSWORD`. `JWT_SECRET` is generated automatically.
4. Check `https://<service>.onrender.com/actuator/health`.

On the free tier the service sleeps after about 15 minutes idle, so the first request afterwards can take around a minute.

## Limitations and next steps

- Simulation only: no real money, KYC or regulatory compliance.
- Single instance. Login throttling is in memory; scaling out needs a shared store such as Redis, or limiting at an API gateway.
- No refresh tokens or multi-factor authentication.
- Audit immutability relies on a database trigger; production would add restricted database roles and external log shipping.
- Possible next steps: migrate to Spring Boot 4, outbox pattern with a message broker for notifications, per-IP rate limiting, statement export.
