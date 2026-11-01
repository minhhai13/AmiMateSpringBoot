package com.minhhai.amimate.service;

import com.minhhai.amimate.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Chặn dò PIN: quá MAX_FAILURES lần sai trên một robot thì khóa LOCK_DURATION. */
@Component
public class ClaimAttemptLimiter {
    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record State(int failures, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public void checkAllowed(String deviceId) {
        State state = states.get(deviceId);
        if (state != null && state.lockedUntil() != null) {
            if (Instant.now().isBefore(state.lockedUntil())) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Nhập sai PIN quá nhiều lần. Vui lòng thử lại sau 15 phút.");
            }
            states.remove(deviceId, state);
        }
    }

    public void recordFailure(String deviceId) {
        states.merge(deviceId, new State(1, null), (old, ignored) -> {
            int failures = old.failures() + 1;
            return failures >= MAX_FAILURES
                    ? new State(0, Instant.now().plus(LOCK_DURATION))
                    : new State(failures, null);
        });
    }

    public void recordSuccess(String deviceId) {
        states.remove(deviceId);
    }
}
