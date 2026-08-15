package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.message.MessageService;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Publicly documented gameplay controls that map cleanly to Paper's supported API. */
public final class ParityGameplayListener implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final NamespacedKey wardenHeartClaimed;
    private final Map<RuleKey<?>, Object> originalRules = new HashMap<>();
    private Objective healthObjective;

    public ParityGameplayListener(JavaPlugin plugin, ConfigService configs, MessageService messages) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.wardenHeartClaimed = new NamespacedKey(plugin, "warden_heart_claimed");
        reload();
    }

    public void reload() {
        Bukkit.getWorlds().forEach(this::applyWorldRules);
        applyHealthIndicator();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyWorldRules(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExperienceSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof ExperienceOrb orb) || !configs.enabled("xp-clumps")) return;
        double radius = Math.max(0.0, configs.main().getDouble("xp-clumps.radius", 4.0));
        int maximum = Math.max(1, configs.main().getInt("xp-clumps.maximum-experience", 10000));
        int total = orb.getExperience();
        for (var nearby : orb.getWorld().getNearbyEntities(orb.getLocation(), radius, radius, radius)) {
            if (!(nearby instanceof ExperienceOrb other) || other.equals(orb) || !other.isValid()) continue;
            if (other.getExperience() > maximum - total) continue;
            total += other.getExperience();
            other.remove();
            if (total >= maximum) break;
        }
        orb.setExperience(total);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent event) {
        if (configs.enabled("shield-tweaks")) {
            event.setCooldown(Math.max(0, configs.main().getInt("shield-tweaks.disable-cooldown-ticks", 5)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShieldDamage(EntityDamageByEntityEvent event) {
        if (!configs.enabled("shield-tweaks") || !(event.getEntity() instanceof Player player) || !player.isBlocking()) return;
        if (configs.main().getBoolean("shield-tweaks.correct-block-sound", true)) {
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        }
        if (configs.main().getBoolean("shield-tweaks.skip-vanilla-damage-ticks", false)) player.setNoDamageTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenInteract(PlayerInteractEntityEvent event) {
        if (!configs.enabled("warden-heart") || !configs.file("items.yml").getBoolean("warden-heart.enabled", false)
                || !event.getRightClicked().getType().name().equals("WARDEN")) return;
        String acquisition = configs.file("items.yml").getString("warden-heart.acquisition", "RIGHT_CLICK").toUpperCase(Locale.ROOT);
        if (!acquisition.equals("RIGHT_CLICK") && !acquisition.equals("BOTH")) return;
        if (event.getRightClicked().getPersistentDataContainer().has(wardenHeartClaimed, PersistentDataType.BYTE)) return;
        Material material = Material.matchMaterial(configs.file("items.yml").getString("warden-heart.material", "ECHO_SHARD"));
        if (material == null || material.isAir()) return;
        ItemStack heart = new ItemStack(material);
        heart.editMeta(meta -> {
            meta.itemName(messages.raw("<gold>Warden Heart</gold>").decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "warden_heart"), PersistentDataType.BYTE, (byte) 1);
            meta.setEnchantmentGlintOverride(true);
        });
        event.getRightClicked().getPersistentDataContainer().set(wardenHeartClaimed, PersistentDataType.BYTE, (byte) 1);
        event.getPlayer().getInventory().addItem(heart).values().forEach(item ->
                event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), item));
    }

    private void applyWorldRules(World world) {
        applyRule(world, "locator-bar", GameRules.LOCATOR_BAR, configs.main().getBoolean("locator-bar.enabled", false));
        applyRule(world, "global-pvp", GameRules.PVP, configs.main().getBoolean("pvp.enabled", true));
        applyRule(world, "one-player-sleep", GameRules.PLAYERS_SLEEPING_PERCENTAGE,
                Math.max(0, Math.min(100, configs.main().getInt("sleep.players-sleeping-percentage", 1))));
    }

    private <T> void applyRule(World world, String feature, GameRule<T> rule, T configured) {
        RuleKey<T> key = new RuleKey<>(world.getUID(), rule);
        if (configs.enabled(feature)) {
            originalRules.putIfAbsent(key, world.getGameRuleValue(rule));
            world.setGameRule(rule, configured);
        } else if (originalRules.containsKey(key)) {
            @SuppressWarnings("unchecked") T original = (T) originalRules.remove(key);
            if (original != null) world.setGameRule(rule, original);
        }
    }

    private void applyHealthIndicator() {
        var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (!configs.enabled("health-indicator")) {
            if (healthObjective != null) healthObjective.unregister();
            healthObjective = null;
            return;
        }
        Objective existing = scoreboard.getObjective("glitg_health");
        healthObjective = existing == null
                ? scoreboard.registerNewObjective("glitg_health", Criteria.HEALTH,
                messages.raw(configs.main().getString("health-indicator.label", "<red>❤</red>")), RenderType.HEARTS)
                : existing;
        healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
    }

    @Override public void close() {
        for (World world : Bukkit.getWorlds()) {
            restore(world, GameRules.LOCATOR_BAR);
            restore(world, GameRules.PVP);
            restore(world, GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        }
        if (healthObjective != null) healthObjective.unregister();
        healthObjective = null;
    }

    private <T> void restore(World world, GameRule<T> rule) {
        RuleKey<T> key = new RuleKey<>(world.getUID(), rule);
        @SuppressWarnings("unchecked") T original = (T) originalRules.remove(key);
        if (original != null) world.setGameRule(rule, original);
    }

    private record RuleKey<T>(UUID world, GameRule<T> rule) {}
}
