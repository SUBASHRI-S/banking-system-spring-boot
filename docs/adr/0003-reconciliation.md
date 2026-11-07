# ADR 0003: Independent reconciliation of balances against the ledger

**Status:** accepted

**Context.** Tests prove the code is correct today. They cannot prove production data is still correct after a bug, a manual fix or tampering.

**Decision.** `ReconciliationService` recomputes every account's balance from `ledger_entries` in one SQL statement (one snapshot) and reports differences. It runs nightly (`app.reconciliation.cron`) and on demand at `GET /api/v1/admin/reconciliation`. The gauge `banksim.reconciliation.mismatches` exposes the count; a non-zero value should alert.

**Consequences.** Full-table aggregation is fine at this size. At scale it becomes incremental (per day, per account range) or runs on a read replica.
