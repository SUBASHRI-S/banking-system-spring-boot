# ADR 0005: Correlation ids and business metrics

**Status:** accepted

**Decision.**
- Every request gets an `X-Request-Id` (a well-formed incoming value is reused, otherwise a UUID). It goes into the log context, the response header, error bodies and audit rows, so one id connects a customer report to log lines and audit entries.
- `TransferMetricsAspect` records `banksim.transfers{outcome,reason}` and `banksim.transfer.duration`. All counters carry the same tag keys because Prometheus rejects inconsistent ones.
- `/actuator/**` except health requires the ADMIN role.

**Test note.** Spring Boot tests disable metrics export, so tests read the `MeterRegistry` bean instead of scraping `/actuator/prometheus`.
