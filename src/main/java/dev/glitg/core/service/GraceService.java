package dev.glitg.core.service;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.persistence.SqliteDatabase;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class GraceService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final SqliteDatabase database;
    private final Clock clock;
    private final BossBar bar;
    private volatile Instant endsAt;
    private volatile Duration totalDuration = Duration.ZERO;
    private BukkitTask task;

    public GraceService(JavaPlugin plugin, ConfigService configs, MessageService messages, SqliteDatabase database, Clock clock) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.database = database;
        this.clock = clock;
        this.bar = BossBar.bossBar(messages.raw("<gold>Grace period</gold>"), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        if (configs.enabled("grace")) restore();
        if (configs.enabled("grace") && endsAt == null && configs.main().getBoolean("grace.active-on-startup", false)) {
            start(Duration.ofSeconds(configs.main().getLong("grace.duration-seconds", 3600)));
        }
    }

    private void restore() {
        try {
            String raw = database.state("grace.ends-at");
            if (raw != null) {
                Instant restored = Instant.ofEpochMilli(Long.parseLong(raw));
                if (restored.isAfter(clock.instant())) {
                    endsAt = restored;
                    String duration = database.state("grace.duration-millis");
                    totalDuration = duration == null ? Duration.between(clock.instant(), restored)
                            : Duration.ofMillis(Math.max(1, Long.parseLong(duration)));
                    schedule();
                }
            }
        } catch (SQLException | NumberFormatException exception) {
            plugin.getLogger().warning("Could not restore grace state: " + exception.getMessage());
        }
    }

    public synchronized void start(Duration duration) {
        if (!configs.enabled("grace")) throw new IllegalStateException("grace feature is disabled");
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("grace duration must be positive");
        endsAt = clock.instant().plus(duration);
        totalDuration = duration;
        persist();
        schedule();
        Bukkit.broadcast(messages.component("grace-start", Map.of("seconds", duration.toSeconds())));
        for (String action : configs.main().getStringList("grace.start-actions")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.startsWith("/") ? action.substring(1) : action);
        }
    }

    public synchronized void stop() {
        stop(true);
    }

    private synchronized void stop(boolean announce) {
        boolean wasActive = endsAt != null;
        endsAt = null;
        totalDuration = Duration.ZERO;
        if (task != null) task.cancel();
        task = null;
        hideBar();
        persist();
        if (announce && wasActive) {
            Bukkit.broadcast(messages.component("grace-end"));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(net.kyori.adventure.title.Title.title(messages.component("grace-title"), messages.component("grace-subtitle")));
            }
        }
    }

    public synchronized void reload() {
        if (!configs.enabled("grace")) {
            stop(false);
        } else if (endsAt == null) {
            restore();
        }
    }

    private synchronized void schedule() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Duration remaining = remaining();
            if (remaining.isZero()) { stop(); return; }
            float progress = Math.max(0.0f, Math.min(1.0f, remaining.toMillis() /
                    (float) Math.max(1, totalDuration.toMillis())));
            bar.progress(progress);
            bar.name(messages.raw("<gold>Grace: " + Math.max(1, remaining.toSeconds()) + "s</gold>"));
            Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(bar));
        }, 0L, 20L);
    }

    public Duration remaining() {
        Instant end = endsAt;
        if (end == null) return Duration.ZERO;
        Duration remaining = Duration.between(clock.instant(), end);
        return remaining.isNegative() || remaining.isZero() ? Duration.ZERO : remaining;
    }

    public boolean active() { return configs.enabled("grace") && !remaining().isZero(); }

    private void persist() {
        try {
            database.putState("grace.ends-at", endsAt == null ? "0" : String.valueOf(endsAt.toEpochMilli()));
            database.putState("grace.duration-millis", String.valueOf(totalDuration.toMillis()));
        }
        catch (SQLException exception) { plugin.getLogger().warning("Could not persist grace state: " + exception.getMessage()); }
    }

    private void hideBar() { Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar)); }

    @Override public synchronized void close() { if (task != null) task.cancel(); hideBar(); }
}
