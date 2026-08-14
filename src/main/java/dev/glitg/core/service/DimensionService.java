package dev.glitg.core.service;

import dev.glitg.core.config.ConfigService;
import org.bukkit.World;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public final class DimensionService {
    private final ConfigService configs;
    private final Clock clock;
    private final Map<World.Environment, Instant> scheduledUnlocks = new EnumMap<>(World.Environment.class);

    public DimensionService(ConfigService configs, Clock clock) {
        this.configs = configs;
        this.clock = clock;
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
        if (!locked) scheduledUnlocks.remove(environment);
    }

    public void scheduleUnlock(World.Environment environment, Duration duration) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("duration must be positive");
        scheduledUnlocks.put(environment, clock.instant().plus(duration));
    }
}
