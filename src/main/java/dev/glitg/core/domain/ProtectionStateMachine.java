package dev.glitg.core.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProtectionStateMachine {
    private final Clock clock;
    private final Map<UUID, Instant> protectedUntil = new HashMap<>();

    public ProtectionStateMachine(Clock clock) {
        this.clock = clock;
    }

    public synchronized void grant(UUID player, Duration duration) {
        if (duration.isNegative()) throw new IllegalArgumentException("duration cannot be negative");
        protectedUntil.put(player, clock.instant().plus(duration));
    }

    public synchronized boolean isProtected(UUID player) {
        Instant until = protectedUntil.get(player);
        if (until == null) return false;
        if (!until.isAfter(clock.instant())) {
            protectedUntil.remove(player);
            return false;
        }
        return true;
    }

    public synchronized void revokeOnOutgoingAttack(UUID player) {
        protectedUntil.remove(player);
    }
}
