# Requirement traceability

Each requirement maps to code, tests and a demo step. The "Run result" column stays empty until **you** have run the test and seen it pass.

| Requirement | Code | Tests | Demo step (Postman folder / smoke script) | Run result |
|---|---|---|---|---|
| BANK-FR-01 Register | `AuthController`, `AuthService.register`, `V1__create_users.sql` | `ApiFlowIT.registerWorksAndDuplicateEmailIs409` (T-API-01) | 01 Auth / Register, Register duplicate | ☐ |
| BANK-FR-02 Login | `AuthService.login`, `JwtService`, `SecurityConfig` | `ApiFlowIT.loginWithWrongPassword...` (T-API-02), `loginReturnsBearerToken...` | 01 Auth / Login, Login wrong password | ☐ |
| BANK-FR-03 Open account | `AccountService.openAccount`, `V2__create_accounts.sql` | `ApiFlowIT` (open account asserts balance 0.00) | 02 Accounts / Open account | ☐ |
| BANK-FR-04 Own accounts only, 404 | `AccountService.getMine/lockOwned` | `ApiFlowIT.readingAnotherUsersAccountGives404` (T-API-05), `AccountServiceTest.depositIntoSomeoneElsesAccount...` | 02 Accounts / Other user's account | ☐ |
| BANK-FR-05 Deposit | `AccountService.deposit`, `Money` | `AccountServiceTest` (T-UNIT-04), `MoneyTest`, `ApiFlowIT.failureCases...` | 02 Accounts / Deposit | ☐ |
| BANK-FR-06 Withdraw | `AccountService.withdraw`, `Account.debit`, DB `CHECK` | `AccountServiceTest.withdrawWithInsufficientFunds...` (T-UNIT-01), `ConcurrentTransferIT.parallelWithdrawals...`, `LedgerIntegrityIT.databaseCheckConstraint...` | 02 Accounts / Withdraw too much | ☐ |
| BANK-FR-07 Atomic transfer | `TransferService.transfer`, `V3`, `V4` | `TransferServiceTest` (T-UNIT-02, T-UNIT-03), `LedgerIntegrityIT.transferFailingMidway...` (T-INT-02), `ApiFlowIT` | 03 Transfers / Transfer, same account, unknown destination | ☐ |
| BANK-FR-08 History | `TransactionService.history`, `TransactionController` | `ApiFlowIT.depositWithdrawTransferAndHistoryEndToEnd` | 04 History | ☐ |
| BANK-FR-09 Freeze / unfreeze | `AdminController`, `AccountService.setStatus`, `Account.assertActive` | `ApiFlowIT.adminCanFreeze...`, `ApiFlowIT.userCallingAdminEndpointGets403` (T-API-04), `LedgerIntegrityIT.frozenAccount...` (T-INT-03) | 05 Admin | ☐ |
| BANK-FR-10 Consistent errors | `GlobalExceptionHandler`, `ErrorResponses`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` | `ApiFlowIT.invalidBodyReturnsTheStandardErrorJson...` (T-API-06) | every failure request | ☐ |
| BANK-FR-11 Idempotent transfer (adv.) | `TransferService` step 4, `V3` unique index | `AdvancedTransferIT.sameIdempotencyKeyTwice...` (T-ADV-01) | 03 Transfers / Transfer with Idempotency-Key (send twice) | ☐ |
| BANK-FR-12 Daily limit (adv.) | `TransferService.enforceDailyLimit`, `TransferProperties` | `AdvancedTransferIT.dailyLimitStopsTransfers...` | set `DAILY_TRANSFER_LIMIT` low and retry | ☐ |
| BANK-NFR-01 Ledger equals balance | `LedgerEntryRepository.netTotalForAccount` | `LedgerIntegrityIT.balanceAlwaysEqualsTheSumOfLedgerEntries` (T-INT-01) | n/a | ☐ |
| BANK-NFR-02 Concurrency safety | `AccountRepository.findByIdForUpdate`, ordered locks in `TransferService` | `ConcurrentTransferIT` (T-CON-01, T-CON-02) | n/a | ☐ |
| BANK-NFR-03 Security | `SecurityConfig`, BCrypt, `JwtProperties` (secret from env) | `ApiFlowIT.noTokenOrGarbageToken...` (T-API-03) | 06 Health and security / No token | ☐ |
| BANK-NFR-04 Reproducible run | `Dockerfile`, `docker-compose.yml` | clean-clone `docker compose up` (manual) | README quick start | ☐ |
| BANK-NFR-05 Observability | actuator health, `RequestLoggingFilter` | `ApiFlowIT.healthEndpointIsPublic` | 06 Health and security / Health | ☐ |
| BANK-NFR-06 CI | `.github/workflows/ci.yml` | green workflow run | Actions tab | ☐ |
