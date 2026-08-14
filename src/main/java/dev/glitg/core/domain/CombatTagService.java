package dev.glitg.core.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatTagService {
    private final Clock clock;
    private final Map<UUID, Tag> tags = new HashMap<>();

    public CombatTagService(Clock clock) {
        this.clock = clock;
    }

    public synchronized void tag(UUID attacker, UUID victim, Duration duration) {
        if (attacker.equals(victim) || duration.isNegative() || duration.isZero()) return;
        Instant expiration = clock.instant().plus(duration);
        tags.put(attacker, new Tag(victim, expiration));
        tags.put(victim, new Tag(attacker, expiration));
    }

    public synchronized Duration remaining(UUID player) {
        Tag tag = tags.get(player);
        if (tag == null) return Duration.ZERO;
        Duration remaining = Duration.between(clock.instant(), tag.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) {
            tags.remove(player);
            return Duration.ZERO;
        }
        return remaining;
    }

    public synchronized boolean isTagged(UUID player) {
        return !remaining(player).isZero();
    }

    public synchronized UUID opponent(UUID player) {
        return isTagged(player) ? tags.get(player).opponent() : null;
    }

    public synchronized void clear(UUID player) {
        tags.remove(player);
    }

    public record Tag(UUID opponent, Instant expiresAt) {}
}
