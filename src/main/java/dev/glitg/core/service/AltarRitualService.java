package dev.glitg.core.service;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.persistence.SqliteDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AltarRitualService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final SqliteDatabase database;
    private final Clock clock;
    private final Map<String, SqliteDatabase.AltarRow> altars = new ConcurrentHashMap<>();
    private final Map<String, String> activeByAltar = new ConcurrentHashMap<>();
    private final Map<String, org.bukkit.scheduler.BukkitTask> tasks = new ConcurrentHashMap<>();

    public AltarRitualService(JavaPlugin plugin, ConfigService configs, MessageService messages, SqliteDatabase database, Clock clock) throws SQLException {
        this.plugin = plugin; this.configs = configs; this.messages = messages; this.database = database; this.clock = clock;
        database.altars().forEach(altar -> altars.put(altar.id(), altar));
        for (var run : database.runningRituals()) {
            var altar = altars.get(run.altarId());
            if (altar == null) {
                plugin.getLogger().severe("Ritual " + run.id() + " references missing altar " + run.altarId() + "; preserving it for manual recovery.");
                continue;
            }
            activeByAltar.put(altar.id(), run.id());
            long remainingSeconds = Math.max(0, (run.completesAt() - clock.millis() + 999) / 1000);
            Bukkit.getScheduler().runTask(plugin, () -> schedule(run.id(), run.ritualId(), altar, remainingSeconds));
        }
    }

    public synchronized String place(Player player, String definition) throws SQLException {
        var section = configs.file("rituals.yml").getConfigurationSection("altars." + definition);
        if (section == null || !section.getBoolean("enabled", false)) throw new IllegalArgumentException("unknown or disabled altar definition");
        Material expected = Material.matchMaterial(section.getString("block", "LODESTONE"));
        var block = player.getTargetBlockExact(6);
        if (block == null || expected == null || block.getType() != expected) throw new IllegalArgumentException("look at a " + (expected == null ? "configured block" : expected.name()));
        String id = definition + "-" + UUID.randomUUID().toString().substring(0, 8);
        Location location = block.getLocation();
        var row = new SqliteDatabase.AltarRow(id, location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), definition);
        database.putAltar(row); altars.put(id, row); return id;
    }

    public synchronized boolean remove(String id) throws SQLException {
        if (activeByAltar.containsKey(id)) throw new IllegalStateException("altar has a running ritual");
        boolean existed = altars.remove(id) != null;
        if (existed) database.removeAltar(id);
        return existed;
    }

    public List<SqliteDatabase.AltarRow> list() { return List.copyOf(altars.values()); }

    public SqliteDatabase.AltarRow at(Location location) {
        return altars.values().stream().filter(row -> row.worldUuid().equals(location.getWorld().getUID())
                && row.x() == location.getBlockX() && row.y() == location.getBlockY() && row.z() == location.getBlockZ()).findFirst().orElse(null);
    }

    public synchronized boolean tryStart(Player player, SqliteDatabase.AltarRow altar) {
        if (!configs.enabled("rituals") || activeByAltar.containsKey(altar.id())) return false;
        var rituals = configs.file("rituals.yml").getConfigurationSection("rituals");
        if (rituals == null) return false;
        ItemStack held = player.getInventory().getItemInMainHand();
        for (String id : rituals.getKeys(false)) {
            String base = "rituals." + id;
            if (!configs.file("rituals.yml").getBoolean(base + ".enabled", false)
                    || !altar.definition().equals(configs.file("rituals.yml").getString(base + ".altar"))) continue;
            Material input = Material.matchMaterial(configs.file("rituals.yml").getString(base + ".input-material", "AIR"));
            int amount = configs.file("rituals.yml").getInt(base + ".input-amount", 1);
            if (held.getType() != input || held.getAmount() < amount) continue;
            long now = clock.millis(); long duration = configs.file("rituals.yml").getLong(base + ".duration-seconds", 10);
            String runId = altar.id() + ":" + id + ":" + UUID.randomUUID();
            try {
                if (!database.beginRitual(runId, id, altar.id(), now, now + duration * 1000L)) return false;
                held.setAmount(held.getAmount() - amount); // consume only after the durable run row exists
                activeByAltar.put(altar.id(), runId);
                schedule(runId, id, altar, duration);
                return true;
            } catch (SQLException exception) {
                plugin.getLogger().severe("Could not start ritual transaction: " + exception.getMessage());
                return false;
            }
        }
        return false;
    }

    private void schedule(String runId, String ritualId, SqliteDatabase.AltarRow altar, long durationSeconds) {
        Location location = location(altar);
        if (location == null) return;
        String base = "rituals." + ritualId;
        Particle particle;
        try { particle = Particle.valueOf(configs.file("rituals.yml").getString(base + ".particle", "SOUL_FIRE_FLAME").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { particle = Particle.SOUL_FIRE_FLAME; }
        long totalTicks = Math.max(1, durationSeconds * 20L);
        final long[] elapsed = {0};
        Particle selected = particle;
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += 10;
            double radius = configs.file("rituals.yml").getDouble(base + ".radius", 3.0);
            if (radius > 0) location.getWorld().spawnParticle(selected, location.clone().add(0.5, 1, 0.5), 12, radius / 2, 0.5, radius / 2, 0.01);
            if (elapsed[0] >= totalTicks) finish(runId, ritualId, altar, location);
        }, 0L, 10L);
        tasks.put(runId, task);
    }

    private synchronized void finish(String runId, String ritualId, SqliteDatabase.AltarRow altar, Location location) {
        org.bukkit.scheduler.BukkitTask task = tasks.remove(runId);
        if (task != null) task.cancel();
        try {
            database.finishRitual(runId);
            String base = "rituals." + ritualId;
            Material material = Material.matchMaterial(configs.file("rituals.yml").getString(base + ".result-material", "AIR"));
            int amount = configs.file("rituals.yml").getInt(base + ".result-amount", 1);
            if (material != null && !material.isAir() && amount > 0) location.getWorld().dropItemNaturally(location.clone().add(0.5, 1, 0.5), new ItemStack(material, amount));
            configs.file("rituals.yml").getStringList(base + ".commands").forEach(command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("<x>", String.valueOf(altar.x())).replace("<y>", String.valueOf(altar.y())).replace("<z>", String.valueOf(altar.z()))));
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not complete ritual " + runId + ": " + exception.getMessage());
        } finally { activeByAltar.remove(altar.id()); }
    }

    private static Location location(SqliteDatabase.AltarRow altar) {
        World world = Bukkit.getWorld(altar.worldUuid());
        return world == null ? null : new Location(world, altar.x(), altar.y(), altar.z());
    }

    @Override public void close() { tasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel); tasks.clear(); }
}
