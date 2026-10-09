package com.preppanel.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory sliding-window rate limiter, keyed by an arbitrary string
 * (IP for anonymous endpoints like login, userId for authenticated LLM-calling
 * endpoints). Good enough for a single-instance v1 deployment; if PrepPanel
 * ever runs more than one backend instance, swap this for a Redis-backed
 * token bucket (Upstash free tier) instead - the call sites won't change.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * @return true if the caller is within the limit and the hit was recorded,
     *         false if the caller is over the limit and should be rejected.
     */
    public synchronized boolean allow(String key, int maxHits, java.time.Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxHits) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }
}
