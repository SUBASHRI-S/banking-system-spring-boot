# ADR 0004: In-memory login throttling

**Status:** accepted, with a known limit

**Context.** Without a limit, an attacker can try unlimited passwords against one account.

**Decision.** After 5 failed logins for an email within 15 minutes, further attempts return HTTP 429 `TOO_MANY_ATTEMPTS`, even with the correct password, until old failures age out. A success clears the counter. Limits are configurable (`MAX_LOGIN_FAILURES`, `LOCKOUT_SECONDS`).

**Known limits.** State is per instance and lost on restart, so it works for a single instance only. Locking by email lets an attacker lock a victim out (denial of service); the usual refinement is limiting per IP and email together. With several instances use a shared store (Redis) or the gateway.
