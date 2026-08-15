package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.item.RuleEngine;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.permission.BypassPolicy;
import dev.glitg.core.persistence.SqliteDatabase;
import dev.glitg.core.service.DimensionService;
import dev.glitg.core.service.KitService;
import dev.glitg.core.service.PostDeathProtectionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public final class LifecycleGameplayListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final RuleEngine rules;
    private final DimensionService dimensions;
    private final KitService kits;
    private final SqliteDatabase database;
    private final Clock clock;
    private final PostDeathProtectionService postDeath;
    private final Map<UUID, List<ItemStack>> immortalItems = new HashMap<>();

    public LifecycleGameplayListener(JavaPlugin plugin, ConfigService configs, MessageService messages, RuleEngine rules,
                                     DimensionService dimensions, KitService kits, SqliteDatabase database,
                                     PostDeathProtectionService postDeath, Clock clock) {
        this.plugin = plugin; this.configs = configs;
        this.messages = messages;
        this.rules = rules;
        this.dimensions = dimensions;
        this.kits = kits;
        this.database = database;
        this.clock = clock;
        this.postDeath = postDeath;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) { enforceDimension(event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { enforceDimension(event); }

    private void enforceDimension(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        if (dimensionBlocked(event.getPlayer(), event.getTo().getWorld().getEnvironment())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "dimension-locked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (dimensionBlocked(player, player.getWorld().getEnvironment())) ejectFromLockedDimension(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        postDeath.restore(player.getUniqueId());
        try {
            long expiry = database.deathBanExpiry(player.getUniqueId());
            if (expiry > clock.millis()) {
                long seconds = (expiry - clock.millis() + 999) / 1000;
                player.kick(messages.component("death-banned", Map.of("seconds", seconds)));
                return;
            } else if (expiry > 0) database.clearDeathBan(player.getUniqueId());
            if (!player.hasPlayedBefore() && configs.enabled("join-kit") && kits.joinEnabled()) kits.give(player, false);
        } catch (SQLException | IOException exception) {
            player.getServer().getLogger().warning("Join-state operation failed for " + player.getName() + ": " + exception.getMessage());
        }
        if (dimensionBlocked(player, player.getWorld().getEnvironment())) {
            player.getScheduler().run(plugin, task -> {
                if (dimensionBlocked(player, player.getWorld().getEnvironment())) ejectFromLockedDimension(player);
            }, null);
        }
    }

    private boolean dimensionBlocked(Player player, World.Environment environment) {
        return configs.enabled("dimensions") && dimensions.locked(environment)
                && !BypassPolicy.bypasses(configs, player, "glitgcore.bypass.dimensions");
    }

    private void ejectFromLockedDimension(Player player) {
        World fallback = Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst().orElse(null);
        if (fallback == null) {
            plugin.getLogger().severe("Cannot eject " + player.getName() + " from a locked dimension: no normal world is loaded");
            return;
        }
        player.teleportAsync(fallback.getSpawnLocation());
        messages.send(player, "dimension-locked");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        boolean hideDeath = shouldHideDeath(event);
        if (hideDeath) event.deathMessage(null);
        if (configs.enabled("protections") && configs.main().getBoolean("protections.post-death.enabled", false)) {
            long seconds = configs.main().getLong("protections.post-death.duration-seconds", 1800);
            if (seconds > 0) {
                postDeath.grant(player.getUniqueId(), java.time.Duration.ofSeconds(seconds));
                messages.send(player, "post-death-granted", Map.of("seconds", seconds));
            }
        }
        if (configs.enabled("protected-items")) {
            List<ItemStack> saved = new ArrayList<>();
            event.getDrops().removeIf(item -> {
                RuleEngine.ProtectedDefinition definition = rules.protectedDefinition(item);
                if (definition != null && definition.immortal()) { saved.add(item.clone()); return true; }
                return false;
            });
            if (!saved.isEmpty()) immortalItems.put(player.getUniqueId(), saved);
        }
        if (configs.enabled("death-system")) {
            String custom = configs.main().getString("death.custom-message", "");
            if (!custom.isBlank() && !hideDeath) event.deathMessage(messages.raw(custom.replace("<player>", player.getName())));
            String soundName = configs.main().getString("death.sound", "");
            if (!soundName.isBlank()) {
                NamespacedKey key = NamespacedKey.fromString(soundName.contains(":") ? soundName : "minecraft:" + soundName.toLowerCase(Locale.ROOT));
                Sound sound = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(key);
                if (sound != null) player.getWorld().playSound(player.getLocation(), sound, 1f, 1f);
                else player.getServer().getLogger().warning("Unknown death sound: " + soundName);
            }
            long banSeconds = configs.main().getLong("death.death-ban-seconds", 0);
            if (banSeconds > 0) {
                try { database.putDeathBan(player.getUniqueId(), clock.millis() + banSeconds * 1000L); player.getScheduler().runDelayed(plugin, scheduled -> player.kick(messages.component("death-banned", Map.of("seconds", banSeconds))), null, 1L); }
                catch (SQLException exception) { player.getServer().getLogger().severe("Could not persist death ban: " + exception.getMessage()); }
            }
        }
    }

    private boolean shouldHideDeath(PlayerDeathEvent event) {
        if (!configs.enabled("miscellaneous")) return false;
        String raw = configs.main().getString("misc.hide-invisible-deaths-until", "");
        if (raw == null || raw.isBlank()) return false;
        try {
            if (!clock.instant().isBefore(java.time.Instant.parse(raw))) return false;
        } catch (java.time.format.DateTimeParseException exception) {
            plugin.getLogger().warning("Invalid misc.hide-invisible-deaths-until: " + raw);
            return false;
        }
        Player dead = event.getEntity();
        Player killer = dead.getKiller();
        return dead.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)
                || (killer != null && killer.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> saved = immortalItems.remove(event.getPlayer().getUniqueId());
        if (saved != null) saved.forEach(item -> event.getPlayer().getInventory().addItem(item).values()
                .forEach(overflow -> event.getPlayer().getWorld().dropItemNaturally(event.getRespawnLocation(), overflow)));
        if (configs.enabled("death-system") && configs.main().getBoolean("death.spectator-on-death", false)) {
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager && configs.enabled("villagers")
                && configs.main().getBoolean("villagers.prevent-killing", false)) event.setDroppedExp(0);
        if (event.getEntity().getType().name().equals("WARDEN") && configs.enabled("warden-heart")
                && configs.file("items.yml").getBoolean("warden-heart.enabled", false)) {
            double chance = configs.file("items.yml").getDouble("warden-heart.drop-chance", 1.0);
            if (Math.random() <= chance) {
                Material material = Material.matchMaterial(configs.file("items.yml").getString("warden-heart.material", "ECHO_SHARD"));
                if (material != null) {
                    ItemStack heart = new ItemStack(material);
                    heart.editMeta(meta -> { meta.itemName(messages.raw("<gold>Warden Heart</gold>").decoration(TextDecoration.ITALIC, false)); meta.getPersistentDataContainer().set(java.util.Objects.requireNonNull(NamespacedKey.fromString("glitgcore:warden_heart")), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1); meta.setEnchantmentGlintOverride(true); });
                    event.getDrops().add(heart);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStorage(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !configs.enabled("protected-items")) return;
        ItemStack item = event.getCurrentItem();
        RuleEngine.ProtectedDefinition definition = rules.protectedDefinition(item);
        if (definition != null && definition.glowing() && item != null) item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
    }

}
