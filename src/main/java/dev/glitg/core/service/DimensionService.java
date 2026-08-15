package dev.glitg.core.service;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.persistence.SqliteDatabase;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public final class DimensionService {
    private final ConfigService configs;
    private final Clock clock;
    private final JavaPlugin plugin;
    private final SqliteDatabase database;
    private final Map<World.Environment, Instant> scheduledUnlocks = new EnumMap<>(World.Environment.class);

    public DimensionService(JavaPlugin plugin, ConfigService configs, SqliteDatabase database, Clock clock) {
        this.plugin = plugin;
        this.configs = configs;
        this.database = database;
        this.clock = clock;
        restore(World.Environment.NETHER);
        restore(World.Environment.THE_END);
    }

    public boolean locked(World.Environment environment) {
        Instant scheduled = scheduledUnlocks.get(environment);
        if (scheduled != null && !scheduled.isAfter(clock.instant())) {
            try { setLocked(environment, false); } catch (IOException ignored) { return true; }
            scheduledUnlocks.remove(environment);
        }
        return switch (environment) {
            case NETHER -> configs.main().getBoolean("dimensions.nether-locked", false);
            case THE_END -> configs.main().getBoolean("dimensions.end-locked", false);
            default -> false;
        };
    }

    public void setLocked(World.Environment environment, boolean locked) throws IOException {
        String key = switch (environment) {
            case NETHER -> "dimensions.nether-locked";
            case THE_END -> "dimensions.end-locked";
            default -> throw new IllegalArgumentException("only Nether and End can be locked");
        };
        configs.main().set(key, locked);
        configs.save("config.yml");
        if (!locked) {
            scheduledUnlocks.remove(environment);
            persist(environment, null);
        }
    }

    public void scheduleUnlock(World.Environment environment, Duration duration) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("duration must be positive");
        Instant expiry = clock.instant().plus(duration);
        scheduledUnlocks.put(environment, expiry);
        persist(environment, expiry);
    }

    private void restore(World.Environment environment) {
        try {
            String raw = database.state(stateKey(environment));
            if (raw != null && !raw.equals("0")) scheduledUnlocks.put(environment, Instant.ofEpochMilli(Long.parseLong(raw)));
        } catch (java.sql.SQLException | NumberFormatException exception) {
            plugin.getLogger().warning("Could not restore dimension unlock: " + exception.getMessage());
        }
    }

    private void persist(World.Environment environment, Instant expiry) {
        try { database.putState(stateKey(environment), expiry == null ? "0" : String.valueOf(expiry.toEpochMilli())); }
        catch (java.sql.SQLException exception) { plugin.getLogger().warning("Could not persist dimension unlock: " + exception.getMessage()); }
    }

    private static String stateKey(World.Environment environment) {
        return "dimension." + (environment == World.Environment.THE_END ? "end" : "nether") + ".unlock-at";
    }
}
