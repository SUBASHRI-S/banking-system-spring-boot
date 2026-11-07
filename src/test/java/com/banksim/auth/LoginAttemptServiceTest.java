package com.banksim.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.banksim.common.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    /** Clock the test can move forward. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock, 3, 600);

    @Test
    void blocksAfterTheLimitIsReached() {
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> service.assertNotBlocked("a@x.com"));
            service.recordFailure("a@x.com");
        }
        assertThrows(TooManyAttemptsException.class, () -> service.assertNotBlocked("a@x.com"));
    }

    @Test
    void otherUsersAreNotAffected() {
        for (int i = 0; i < 3; i++) service.recordFailure("a@x.com");
        assertDoesNotThrow(() -> service.assertNotBlocked("b@x.com"));
    }

    @Test
    void failuresAgeOutAfterTheWindow() {
        for (int i = 0; i < 3; i++) service.recordFailure("a@x.com");
        clock.advance(Duration.ofSeconds(601));
        assertDoesNotThrow(() -> service.assertNotBlocked("a@x.com"));
    }

    @Test
    void successClearsTheCounter() {
        service.recordFailure("a@x.com");
        service.recordFailure("a@x.com");
        service.recordSuccess("a@x.com");
        service.recordFailure("a@x.com");
        assertDoesNotThrow(() -> service.assertNotBlocked("a@x.com"));
    }
}
