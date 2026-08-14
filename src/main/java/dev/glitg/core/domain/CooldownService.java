package dev.glitg.core.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownService {
    private final Clock clock;
    private final Map<Key, Instant> expirations = new HashMap<>();

    public CooldownService(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(UUID player, String action, Duration duration) {
        if (duration.isNegative()) throw new IllegalArgumentException("duration cannot be negative");
        Key key = new Key(player, action.toLowerCase());
        Instant now = clock.instant();
        Instant existing = expirations.get(key);
        if (existing != null && existing.isAfter(now)) return false;
        expirations.put(key, now.plus(duration));
        return true;
    }

    public synchronized Duration remaining(UUID player, String action) {
        Key key = new Key(player, action.toLowerCase());
        Instant expiration = expirations.get(key);
        if (expiration == null) return Duration.ZERO;
        Duration remaining = Duration.between(clock.instant(), expiration);
        if (remaining.isNegative() || remaining.isZero()) {
            expirations.remove(key);
            return Duration.ZERO;
        }
        return remaining;
    }

    public synchronized void reset(UUID player, String action) {
        expirations.remove(new Key(player, action.toLowerCase()));
    }

    public synchronized void resetAll(UUID player) {
        expirations.keySet().removeIf(key -> key.player().equals(player));
    }

    private record Key(UUID player, String action) {}
}
