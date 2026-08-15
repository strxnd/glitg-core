package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.domain.CombatTagService;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.permission.BypassPolicy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

public final class CombatRestrictionListener implements Listener {
    private final ConfigService configs;
    private final MessageService messages;
    private final CombatTagService combat;

    public CombatRestrictionListener(ConfigService configs, MessageService messages, CombatTagService combat) {
        this.configs = configs;
        this.messages = messages;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player
                && restricted(player, "combat.restrictions.elytra")) block(player, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET && restricted(event.getPlayer(), "combat.restrictions.lava")) {
            block(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (restricted(event.getPlayer(), "combat.restrictions.draining")) block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material material = event.getBlockPlaced().getType();
        if (isIce(material) && restricted(event.getPlayer(), "combat.restrictions.ice")) {
            block(event.getPlayer(), event);
        } else if ((material == Material.SPONGE || material == Material.WET_SPONGE)
                && restricted(event.getPlayer(), "combat.restrictions.draining")) {
            block(event.getPlayer(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getType() != InventoryType.CRAFTING
                && restricted(player, "combat.restrictions.container-restocking")) block(player, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !restricted(player, "combat.restrictions.armour-switching")) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        boolean armourSlot = event.getSlotType() == InventoryType.SlotType.ARMOR;
        boolean armourMove = isArmour(current) || isArmour(cursor) || isArmour(hotbar);
        if (armourSlot || armourMove) block(player, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isArmour(event.getItem().getItemStack())
                && restricted(player, "combat.restrictions.armour-restocking")) block(player, event);
    }

    private boolean restricted(Player player, String path) {
        return configs.enabled("combat-tag") && combat.isTagged(player.getUniqueId())
                && configs.main().getBoolean(path, false)
                && !BypassPolicy.bypasses(configs, player, "glitgcore.bypass.combat");
    }

    private void block(Player player, org.bukkit.event.Cancellable event) {
        event.setCancelled(true);
        messages.send(player, "combat-action-blocked");
    }

    private static boolean isIce(Material material) {
        return material == Material.ICE || material == Material.PACKED_ICE
                || material == Material.BLUE_ICE || material == Material.FROSTED_ICE;
    }

    private static boolean isArmour(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || name.equals("ELYTRA");
    }
}
