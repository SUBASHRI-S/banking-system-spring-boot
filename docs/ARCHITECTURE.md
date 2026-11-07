# Architecture

## Request path

```mermaid
flowchart LR
    C[Client] --> F[RequestLoggingFilter<br/>X-Request-Id]
    F --> S[Spring Security<br/>JWT, roles]
    S --> K[Controllers<br/>validation]
    K --> A[AuditAspect / MetricsAspect]
    A --> V[Services<br/>@Transactional]
    V --> R[Repositories]
    R --> D[(PostgreSQL 16<br/>Flyway V1-V5)]
```

## Transfer

```mermaid
sequenceDiagram
    participant U as User
    participant T as TransferService
    participant DB as PostgreSQL
    participant AU as Audit (REQUIRES_NEW)
    U->>T: POST /transfers (Idempotency-Key)
    T->>DB: replay check on key
    T->>DB: lock both accounts, lowest id first (FOR UPDATE)
    T->>DB: validate funds, status, daily limit
    T->>DB: debit, credit, transfer row, 2 ledger rows
    DB-->>T: commit (or rollback of everything)
    T-->>AU: record COMPLETED or REJECTED
    T-->>U: 201 or error JSON
```

## Controls

| Risk | Control | Where |
|---|---|---|
| Lost update, double spend | Row locks | `AccountRepository.findByIdForUpdate` |
| Deadlock | Global lock order | `TransferService` |
| Half-completed transfer | Single transaction | `TransferService` |
| Duplicate submission | Idempotency key | `V3`, `TransferService` |
| Negative balance | App check plus DB `CHECK` | `Account.debit`, `V2` |
| Silent data drift | Ledger reconciliation | `ReconciliationService` |
| Repudiation | Append-only audit log | `AuditAspect`, `V5` |
| Password guessing | Throttle plus BCrypt | `LoginAttemptService` |
| Account enumeration | 404 for foreign accounts, same login error | services |
| Hard to debug in production | Request id, metrics | filter, `TransferMetricsAspect` |
| Layering erosion | ArchUnit rules | `ArchitectureTest` |
