# ADR 0002: Audit trail written by an AOP aspect, in its own transaction

**Status:** accepted

**Context.** Regulators and security teams need to know who did what, including rejected attempts. Putting audit calls inside each service method mixes concerns, and an audit row written inside the business transaction disappears when that transaction rolls back, which is exactly when the record matters most (a rejected transfer).

**Decision.** `AuditAspect` wraps `register`, `login`, `transfer` and `setStatus`. It has `@Order(HIGHEST_PRECEDENCE)` so it sits outside the `@Transactional` proxy, and `AuditService.record` uses `REQUIRES_NEW`. A failed business transaction therefore still leaves its audit row. Audit failures are logged and never break the business call.

**Immutability.** Migration V5 adds a trigger that rejects `UPDATE` and `DELETE` on `audit_log`, so the rule holds even for a buggy or compromised application. A database owner can still drop the trigger; production would also restrict the application role and ship logs to external storage.

**Trade-off.** If the audit write fails, the business action still succeeds (availability over completeness). A stricter bank would fail closed. This is a documented choice.
