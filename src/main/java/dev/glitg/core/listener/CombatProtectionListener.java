package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.api.GLITGCombatTagEvent;
import dev.glitg.core.config.DurationParser;
import dev.glitg.core.domain.CombatTagService;
import dev.glitg.core.domain.CooldownService;
import dev.glitg.core.domain.DamagePolicy;
import dev.glitg.core.integration.IntegrationManager;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.permission.BypassPolicy;
import dev.glitg.core.service.GraceService;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatProtectionListener implements Listener {
    private final ConfigService configs;
    private final MessageService messages;
    private final CombatTagService combat;
    private final CooldownService cooldowns;
    private final GraceService grace;
    private final IntegrationManager integrations;
    private final Clock clock;
    private volatile DamagePolicy damagePolicy;
    private final Map<UUID, Long> lastMovement = new HashMap<>();

    public CombatProtectionListener(ConfigService configs, MessageService messages, CombatTagService combat,
                                    CooldownService cooldowns, GraceService grace, IntegrationManager integrations, Clock clock) {
        this.configs = configs;
        this.messages = messages;
        this.combat = combat;
        this.cooldowns = cooldowns;
        this.grace = grace;
        this.integrations = integrations;
        this.clock = clock;
        reload();
    }

    public void reload() {
        Map<String, Double> caps = new HashMap<>();
        var section = configs.main().getConfigurationSection("damage-caps");
        if (section != null) section.getKeys(false).forEach(key -> caps.put(key, section.getDouble(key)));
        damagePolicy = new DamagePolicy(caps);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player player ? player : null;
        Player attacker = attackingPlayer(event.getDamager());
        if (victim != null && attacker != null && !victim.equals(attacker)) {
            if (grace.active() || protectedFrom(attacker, victim)) {
                event.setCancelled(true);
                return;
            }
            if (configs.enabled("combat-tag") && !BypassPolicy.bypasses(configs, attacker, "glitgcore.bypass.combat")) {
                Duration duration = Duration.ofSeconds(configs.main().getLong("combat.duration-seconds", 15));
                var tagEvent = new GLITGCombatTagEvent(attacker, victim, duration);
                Bukkit.getPluginManager().callEvent(tagEvent);
                if (!tagEvent.isCancelled()) {
                    combat.tag(attacker.getUniqueId(), victim.getUniqueId(), tagEvent.duration());
                    messages.send(attacker, "combat-start", Map.of("seconds", tagEvent.duration().toSeconds()));
                    messages.send(victim, "combat-start", Map.of("seconds", tagEvent.duration().toSeconds()));
                }
            }
        }
        applyWeaponCooldown(event, attacker);
        applyCap(event, damageSource(event));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        String source = damageSource(event);
        if (source != null) applyCap(event, source);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player) || BypassPolicy.bypasses(configs, player, "glitgcore.bypass.cooldowns")) return;
        String action = switch (projectile.getType().name()) {
            case "ENDER_PEARL" -> "ender_pearl";
            case "WIND_CHARGE", "BREEZE_WIND_CHARGE" -> "wind_charge";
            case "TRIDENT" -> "trident";
            default -> null;
        };
        if (action != null && !acquire(player, action)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE && !acquire(event.getPlayer(), "enchanted_golden_apple")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShield(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.SHIELD && !acquire(event.getPlayer(), "shield")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!configs.enabled("combat-tag") || !configs.main().getBoolean("combat.block-commands", true)
                || !combat.isTagged(event.getPlayer().getUniqueId())
                || BypassPolicy.bypasses(configs, event.getPlayer(), "glitgcore.bypass.combat")) return;
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        Set<String> whitelist = configs.main().getStringList("combat.whitelisted-commands").stream()
                .map(value -> value.toLowerCase(Locale.ROOT).replaceFirst("^/", ""))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!whitelist.contains(command)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "combat-command");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) lastMovement.put(event.getPlayer().getUniqueId(), clock.millis());
        if (!configs.enabled("combat-tag") || !configs.main().getBoolean("combat.block-safe-regions", true)
                || !combat.isTagged(event.getPlayer().getUniqueId()) || event.getTo() == null
                || BypassPolicy.bypasses(configs, event.getPlayer(), "glitgcore.bypass.combat")) return;
        boolean wasSafe = integrations.isSafeRegion(event.getPlayer(), event.getFrom());
        boolean nowSafe = integrations.isSafeRegion(event.getPlayer(), event.getTo());
        if (!wasSafe && nowSafe) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "combat-command");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastMovement.remove(event.getPlayer().getUniqueId());
        if (!combat.isTagged(event.getPlayer().getUniqueId())) return;
        if (BypassPolicy.bypasses(configs, event.getPlayer(), "glitgcore.bypass.combat")) {
            combat.clear(event.getPlayer().getUniqueId());
            return;
        }
        String action = configs.main().getString("combat.disconnect-action", "KILL").toUpperCase(Locale.ROOT);
        if (action.equals("KILL") && !event.getPlayer().isDead()) event.getPlayer().setHealth(0.0);
        combat.clear(event.getPlayer().getUniqueId());
    }

    private boolean protectedFrom(Player attacker, Player victim) {
        if (!configs.enabled("protections") || BypassPolicy.bypasses(configs, attacker, "glitgcore.bypass.protection")) return false;
        if (isAfkProtected(victim) || isNaked(victim) || isNewPlayer(victim)) return true;
        // A protected player cannot attack while retaining asymmetric safety.
        return isAfkProtected(attacker) || isNaked(attacker) || isNewPlayer(attacker);
    }

    private boolean isAfkProtected(Player player) {
        if (!configs.main().getBoolean("protections.afk.enabled", false)) return false;
        long threshold = configs.main().getLong("protections.afk.activation-seconds", 300) * 1000L;
        return clock.millis() - lastMovement.getOrDefault(player.getUniqueId(), clock.millis()) >= threshold;
    }

    private boolean isNaked(Player player) {
        if (!configs.main().getBoolean("protections.naked.enabled", false)) return false;
        return java.util.Arrays.stream(player.getInventory().getArmorContents()).allMatch(item -> item == null || item.getType().isAir());
    }

    private boolean isNewPlayer(Player player) {
        if (!configs.main().getBoolean("protections.new-player.enabled", false)) return false;
        long duration = configs.main().getLong("protections.new-player.duration-seconds", 3600) * 1000L;
        return clock.millis() - player.getFirstPlayed() < duration;
    }

    private void applyWeaponCooldown(EntityDamageByEntityEvent event, Player attacker) {
        if (attacker == null || BypassPolicy.bypasses(configs, attacker, "glitgcore.bypass.cooldowns") || !configs.enabled("cooldowns")) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String material = weapon.getType().name();
        String action = material.equals("MACE") ? "mace" : material.equals("SPEAR") ? "spear" : null;
        boolean lunge = weapon.getEnchantments().keySet().stream().anyMatch(enchantment -> enchantment.getKey().getKey().equals("lunge"));
        if (lunge) action = "lunge";
        if (action != null && !acquire(attacker, action)) event.setCancelled(true);
    }

    private boolean acquire(Player player, String action) {
        if (!configs.enabled("cooldowns") || BypassPolicy.bypasses(configs, player, "glitgcore.bypass.cooldowns")) return true;
        Duration duration = DurationParser.parse(configs.main().get("cooldowns." + action, "0s"));
        if (cooldowns.tryAcquire(player.getUniqueId(), action, duration)) {
            Material material = Material.matchMaterial(action.toUpperCase(Locale.ROOT));
            if (material != null) player.setCooldown(material, (int) Math.min(Integer.MAX_VALUE, duration.toMillis() / 50));
            return true;
        }
        long seconds = (cooldowns.remaining(player.getUniqueId(), action).toMillis() + 999) / 1000;
        messages.send(player, "cooldown", Map.of("action", action, "seconds", seconds));
        return false;
    }

    private void applyCap(EntityDamageEvent event, String source) {
        if (!configs.enabled("damage-caps") || source == null) return;
        if (event.getEntity() instanceof Player player && BypassPolicy.bypasses(configs, player, "glitgcore.bypass.damagecaps")) return;
        double finalDamage = event.getFinalDamage();
        double capped = damagePolicy.cap(source, finalDamage);
        if (capped < finalDamage && finalDamage > 0) event.setDamage(event.getDamage() * capped / finalDamage);
    }

    private static String damageSource(EntityDamageEvent event) {
        if (event.getDamageSource().getDamageType().equals(DamageType.MACE_SMASH)) return "mace";
        if (event.getDamageSource().getDamageType().equals(DamageType.SPEAR)) return "spear";
        if (event.getDamageSource().getDamageType().equals(DamageType.BAD_RESPAWN_POINT)) {
            var location = event.getDamageSource().getDamageLocation();
            if (location != null) {
                String material = location.getBlock().getType().name();
                if (material.equals("RESPAWN_ANCHOR")) return "respawn_anchor";
                if (material.endsWith("BED")) return "bed_explosion";
            }
            return "bad_respawn_point";
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) return "fall";
        if (event instanceof EntityDamageByBlockEvent block && block.getDamager() != null) {
            String type = block.getDamager().getType().name();
            if (type.contains("RESPAWN_ANCHOR")) return "respawn_anchor";
            if (type.endsWith("BED")) return "bed_explosion";
        }
        if (event instanceof EntityDamageByEntityEvent entityEvent) return damageSource(entityEvent);
        return null;
    }

    private static String damageSource(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Player player = attackingPlayer(damager);
        if (player != null) {
            String weapon = player.getInventory().getItemInMainHand().getType().name();
            if (weapon.equals("MACE")) return "mace";
            if (weapon.equals("SPEAR")) return "spear";
        }
        if (damager instanceof EnderCrystal) return "end_crystal";
        if (damager instanceof ExplosiveMinecart) return "tnt_minecart";
        if (damager instanceof TNTPrimed) return "tnt";
        if (damager instanceof Projectile) return "projectile";
        return null;
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player ? player : null;
        }
        return null;
    }
}
