# ADR 0001: Pessimistic row locks for transfers, lowest account id first

**Status:** accepted

**Context.** Two simultaneous requests can read the same balance and both spend it (lost update). Two opposite transfers (A to B and B to A) can each lock one row and wait for the other (deadlock).

**Options.**
1. Optimistic locking (`@Version`) with retry. Cheap when conflicts are rare, but hot accounts produce retry storms and the client sees spurious failures.
2. Serializable isolation. Correct, but aborts transactions on any conflict and needs retry logic everywhere.
3. `SELECT ... FOR UPDATE` on both rows, always lowest id first.

**Decision.** Option 3. Money movement is a hot, correctness-critical path where waiting briefly is better than failing and retrying. A global lock order removes the deadlock cycle.

**Evidence.** `ConcurrentTransferIT` runs parallel and opposite-direction transfers; `scripts/verify-locking.sh` showed 0 deadlocks with ordered locks against 46 with naive order (50 concurrent opposite transfers, PostgreSQL 16).

**Consequences.** Throughput on a single hot account is serialised by design. At much larger scale this is revisited with partitioning or an event-sourced ledger.
