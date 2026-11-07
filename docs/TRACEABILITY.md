# Requirement traceability

Each requirement maps to the code that implements it, the tests that verify it, and a way to see it working. All listed tests run in `mvn verify` and in CI.

| Requirement | Code | Tests | Demonstration (Postman folder / smoke script) |
|---|---|---|---|
| BANK-FR-01 Register | `AuthController`, `AuthService.register`, `V1__create_users.sql` | `ApiFlowIT.registerWorksAndDuplicateEmailIs409` | 01 Auth / Register, Register duplicate |
| BANK-FR-02 Login | `AuthService.login`, `JwtService`, `SecurityConfig` | `ApiFlowIT.loginWithWrongPassword...`, `loginReturnsBearerToken...` | 01 Auth / Login, Login wrong password |
| BANK-FR-03 Open account | `AccountService.openAccount`, `V2__create_accounts.sql` | `ApiFlowIT` (open account asserts balance 0.00) | 02 Accounts / Open account |
| BANK-FR-04 Own accounts only, 404 | `AccountService.getMine/lockOwned` | `ApiFlowIT.readingAnotherUsersAccountGives404`, `AccountServiceTest.depositIntoSomeoneElsesAccount...` | 02 Accounts / Other user's account |
| BANK-FR-05 Deposit | `AccountService.deposit`, `Money` | `AccountServiceTest`, `MoneyTest`, `ApiFlowIT.failureCases...` | 02 Accounts / Deposit |
| BANK-FR-06 Withdraw | `AccountService.withdraw`, `Account.debit`, DB `CHECK` | `AccountServiceTest.withdrawWithInsufficientFunds...`, `ConcurrentTransferIT.parallelWithdrawals...`, `LedgerIntegrityIT.databaseCheckConstraint...` | 02 Accounts / Withdraw too much |
| BANK-FR-07 Atomic transfer | `TransferService.transfer`, `V3`, `V4` | `TransferServiceTest`, `LedgerIntegrityIT.transferFailingMidway...`, `ApiFlowIT` | 03 Transfers / Transfer, same account, unknown destination |
| BANK-FR-08 History | `TransactionService.history`, `TransactionController` | `ApiFlowIT.depositWithdrawTransferAndHistoryEndToEnd` | 04 History |
| BANK-FR-09 Freeze / unfreeze | `AdminController`, `AccountService.setStatus`, `Account.assertActive` | `ApiFlowIT.adminCanFreeze...`, `ApiFlowIT.userCallingAdminEndpointGets403`, `LedgerIntegrityIT.frozenAccount...` | 05 Admin |
| BANK-FR-10 Consistent errors | `GlobalExceptionHandler`, `ErrorResponses`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` | `ApiFlowIT.invalidBodyReturnsTheStandardErrorJson...` | every failure request |
| BANK-FR-11 Idempotent transfer  | `TransferService` step 4, `V3` unique index | `AdvancedTransferIT.sameIdempotencyKeyTwice...` | 03 Transfers / Transfer with Idempotency-Key (send twice) |
| BANK-FR-12 Daily limit  | `TransferService.enforceDailyLimit`, `TransferProperties` | `AdvancedTransferIT.dailyLimitStopsTransfers...` | set `DAILY_TRANSFER_LIMIT` low and retry |
| BANK-NFR-01 Ledger equals balance | `LedgerEntryRepository.netTotalForAccount` | `LedgerIntegrityIT.balanceAlwaysEqualsTheSumOfLedgerEntries` | n/a |
| BANK-NFR-02 Concurrency safety | `AccountRepository.findByIdForUpdate`, ordered locks in `TransferService` | `ConcurrentTransferIT` | n/a |
| BANK-NFR-03 Security | `SecurityConfig`, BCrypt, `JwtProperties` (secret from env) | `ApiFlowIT.noTokenOrGarbageToken...` | 06 Health and security / No token |
| BANK-NFR-04 Reproducible run | `Dockerfile`, `docker-compose.yml` | clean-clone `docker compose up` (manual) | README quick start |
| BANK-NFR-05 Observability | actuator health, `RequestLoggingFilter` | `ApiFlowIT.healthEndpointIsPublic` | 06 Health and security / Health |
| BANK-NFR-06 CI | `.github/workflows/ci.yml` | green workflow run | Actions tab |
| BANK-FR-13 Audit trail  | `AuditAspect`, `AuditService`, `AuditController`, `V5` (append-only trigger) | `AuditTrailIT` (transfer, login, immutability, admin-only read) | `GET /api/v1/admin/audit-events` as admin |
| BANK-FR-14 Reconciliation  | `ReconciliationService`, `ReconciliationController` | `ReconciliationIT` (tamper detected, repaired, admin-only) | `GET /api/v1/admin/reconciliation` as admin |
| BANK-NFR-07 Brute-force protection  | `LoginAttemptService`, `TooManyAttemptsException` | `LoginAttemptServiceTest`, `LoginThrottleIT` | six wrong logins, then HTTP 429 |
| BANK-NFR-08 Operational visibility  | `TransferMetricsAspect`, `RequestLoggingFilter` (X-Request-Id), `/actuator/prometheus` (admin) | `ObservabilityIT` | `X-Request-Id` header on any response |
| BANK-NFR-09 Enforced architecture  | ArchUnit rules | `ArchitectureTest` | `mvn verify` |
