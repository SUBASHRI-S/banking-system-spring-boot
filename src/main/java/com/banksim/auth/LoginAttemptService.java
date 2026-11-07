package com.banksim.auth;

import com.banksim.common.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection (BANK-NFR-07): after N failed sign-ins for the same email inside a time window,
 * further attempts are rejected with HTTP 429 until old failures age out. A success clears the counter.
 *
 * Deliberate limit: state is kept in memory, so it protects ONE instance. Behind several instances this
 * belongs in a shared store (for example Redis) or at the API gateway. See docs/adr/0006.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock,
                               @Value("${app.security.max-login-failures:5}") int maxFailures,
                               @Value("${app.security.lockout-seconds:900}") long lockoutSeconds) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofSeconds(lockoutSeconds);
    }

    public void assertNotBlocked(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= maxFailures) {
                throw new TooManyAttemptsException();
            }
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > MAX_TRACKED_KEYS) {
            purgeExpired();
        }
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }

    private void purgeExpired() {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }
}
