# Troubleshooting log

Rule from the design document: **only real problems you solved yourself.** Nothing has been invented here. Add an entry each time you hit and fix something.

Template:

```
## <short title>  (date)
Symptom:
Cause:
Fix:
How I verified:
```

---

## Things that may bite on a first run (not yet encountered, listed as places to look)

These are checks, not solved problems. Move one into a real entry only after it actually happens to you.

- **Testcontainers cannot reach Docker / "client version is too old":** Docker must be running. `pom.xml` sets `testcontainers.version` to a release newer than the Spring Boot default because older ones fail against Docker Engine 29. If that version cannot be resolved, use the newest 1.21.x or 2.x release from Maven Central.
- **`@MockitoSpyBean` on the repository in `LedgerIntegrityIT`:** if Mockito refuses to spy on the Spring Data proxy, the alternative is to break the transfer midway another way (for example a test-only failing `TransferProperties`/`Clock` bean) while keeping the same assertions.
- **App refuses to start with a message about `app.jwt.secret`:** `JWT_SECRET` is missing or shorter than 32 characters. That is intentional.
- **Neon connection errors:** use the direct connection, `sslmode=require`, JDBC URL form, and allow time for the compute to wake.
