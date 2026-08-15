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
    private BukkitTask task;

    public GraceService(JavaPlugin plugin, ConfigService configs, MessageService messages, SqliteDatabase database, Clock clock) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.database = database;
        this.clock = clock;
        this.bar = BossBar.bossBar(messages.raw("<gold>Grace period</gold>"), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        restore();
        if (endsAt == null && configs.main().getBoolean("grace.active-on-startup", false)) {
            start(Duration.ofSeconds(configs.main().getLong("grace.duration-seconds", 3600)));
        }
    }

    private void restore() {
        try {
            String raw = database.state("grace.ends-at");
            if (raw != null) {
                Instant restored = Instant.ofEpochMilli(Long.parseLong(raw));
                if (restored.isAfter(clock.instant())) { endsAt = restored; schedule(); }
            }
        } catch (SQLException | NumberFormatException exception) {
            plugin.getLogger().warning("Could not restore grace state: " + exception.getMessage());
        }
    }

    public synchronized void start(Duration duration) {
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("grace duration must be positive");
        endsAt = clock.instant().plus(duration);
        persist();
        schedule();
        Bukkit.broadcast(messages.component("grace-start", Map.of("seconds", duration.toSeconds())));
        for (String action : configs.main().getStringList("grace.start-actions")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.startsWith("/") ? action.substring(1) : action);
        }
    }

    public synchronized void stop() {
        if (endsAt == null) return;
        endsAt = null;
        if (task != null) task.cancel();
        task = null;
        hideBar();
        persist();
        Bukkit.broadcast(messages.component("grace-end"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(net.kyori.adventure.title.Title.title(messages.component("grace-title"), messages.component("grace-subtitle")));
        }
    }

    private synchronized void schedule() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Duration remaining = remaining();
            if (remaining.isZero()) { stop(); return; }
            float progress = Math.max(0.0f, Math.min(1.0f, remaining.toMillis() /
                    (float) Duration.ofSeconds(configs.main().getLong("grace.duration-seconds", 3600)).toMillis()));
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

    public boolean active() { return !remaining().isZero(); }

    private void persist() {
        try { database.putState("grace.ends-at", endsAt == null ? "0" : String.valueOf(endsAt.toEpochMilli())); }
        catch (SQLException exception) { plugin.getLogger().warning("Could not persist grace state: " + exception.getMessage()); }
    }

    private void hideBar() { Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar)); }

    @Override public synchronized void close() { if (task != null) task.cancel(); hideBar(); }
}
