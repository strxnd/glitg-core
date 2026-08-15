package dev.glitg.core.service;

import dev.glitg.core.persistence.SqliteDatabase;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PostDeathProtectionService {
    private final JavaPlugin plugin;
    private final SqliteDatabase database;
    private final Clock clock;
    private final Map<UUID, Instant> protectedUntil = new HashMap<>();

    public PostDeathProtectionService(JavaPlugin plugin, SqliteDatabase database, Clock clock) {
        this.plugin = plugin;
        this.database = database;
        this.clock = clock;
    }

    public synchronized void restore(UUID player) {
        try {
            long expiry = database.protectionExpiry(player);
            if (expiry > clock.millis()) protectedUntil.put(player, Instant.ofEpochMilli(expiry));
            else if (expiry > 0) database.clearProtection(player);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not restore post-death protection: " + exception.getMessage());
        }
    }

    public synchronized void grant(UUID player, Duration duration) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("protection duration must be positive");
        Instant expiry = clock.instant().plus(duration);
        protectedUntil.put(player, expiry);
        try { database.putProtection(player, expiry.toEpochMilli()); }
        catch (SQLException exception) { plugin.getLogger().warning("Could not persist post-death protection: " + exception.getMessage()); }
    }

    public synchronized boolean isProtected(UUID player) { return !remaining(player).isZero(); }

    public synchronized Duration remaining(UUID player) {
        Instant expiry = protectedUntil.get(player);
        if (expiry == null) return Duration.ZERO;
        Duration remaining = Duration.between(clock.instant(), expiry);
        if (remaining.isNegative() || remaining.isZero()) { revoke(player); return Duration.ZERO; }
        return remaining;
    }

    public synchronized void revoke(UUID player) {
        protectedUntil.remove(player);
        try { database.clearProtection(player); }
        catch (SQLException exception) { plugin.getLogger().warning("Could not clear post-death protection: " + exception.getMessage()); }
    }
}
