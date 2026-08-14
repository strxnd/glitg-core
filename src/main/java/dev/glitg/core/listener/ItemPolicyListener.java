package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.domain.ItemAction;
import dev.glitg.core.item.BukkitItemAdapter;
import dev.glitg.core.item.EnchantPolicyService;
import dev.glitg.core.item.PotionPolicyService;
import dev.glitg.core.item.RuleEngine;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.permission.BypassPolicy;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ItemPolicyListener implements Listener {
    private final ConfigService configs;
    private final MessageService messages;
    private final RuleEngine rules;
    private final EnchantPolicyService enchants;
    private final PotionPolicyService potions;
    private final BukkitItemAdapter adapter;

    public ItemPolicyListener(ConfigService configs, MessageService messages, RuleEngine rules,
                              EnchantPolicyService enchants, PotionPolicyService potions, BukkitItemAdapter adapter) {
        this.configs = configs;
        this.messages = messages;
        this.rules = rules;
        this.enchants = enchants;
        this.potions = potions;
        this.adapter = adapter;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || bypass(player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (blocked(item, ItemAction.PICKUP) || policyViolation(item) || exceedsLimit(player, item)) {
            event.setCancelled(true);
            tellBlocked(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (bypass(event.getPlayer())) return;
        ItemStack item = event.getItemDrop().getItemStack();
        if (blocked(item, ItemAction.DROPPING)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "blocked-item");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (bypass(event.getPlayer())) return;
        ItemStack item = event.getItem();
        if (blocked(item, ItemAction.INTERACT) || policyViolation(item)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "blocked-item");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || bypass(player)) return;
        ItemStack result = event.getCurrentItem();
        if (blocked(result, ItemAction.CRAFT) || exceedsLimit(player, result) || policyViolation(result)) {
            event.setCancelled(true);
            tellBlocked(player, result);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;
        Player player = firstPlayer(event.getViewers());
        if (player != null && bypass(player)) return;
        if (blocked(result, ItemAction.CRAFT) || policyViolation(result)) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || bypass(player)) return;
        var candidates = new ArrayList<ItemStack>();
        candidates.add(event.getCurrentItem());
        candidates.add(event.getCursor());
        if (event.getHotbarButton() >= 0) candidates.add(player.getInventory().getItem(event.getHotbarButton()));
        for (ItemStack item : candidates) {
            if (item == null || item.getType().isAir()) continue;
            boolean storage = event.getClickedInventory() != null && event.getClickedInventory() != player.getInventory();
            boolean trade = event.getView().getTopInventory().getType() == InventoryType.MERCHANT;
            boolean equip = event.getSlotType() == InventoryType.SlotType.ARMOR;
            RuleEngine.ProtectedDefinition definition = rules.protectedDefinition(item);
            if (blocked(item, ItemAction.INVENTORY_MOVE) || (storage && blocked(item, ItemAction.STORAGE))
                    || (trade && blocked(item, ItemAction.TRADE)) || (equip && blocked(item, ItemAction.EQUIP))
                    || (storage && definition != null && definition.stopStorage()) || policyViolation(item)) {
                event.setCancelled(true);
                messages.send(player, "blocked-item");
                return;
            }
        }
        ItemStack incoming = incomingToPlayer(event, player);
        if (incoming != null && exceedsLimit(player, incoming)) {
            event.setCancelled(true);
            tellBlocked(player, incoming);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || bypass(player)) return;
        ItemStack item = event.getOldCursor();
        if (blocked(item, ItemAction.INVENTORY_MOVE) || policyViolation(item) || exceedsLimit(player, item)) {
            event.setCancelled(true);
            tellBlocked(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        RuleEngine.ProtectedDefinition definition = rules.protectedDefinition(item);
        if (blocked(item, ItemAction.INVENTORY_MOVE) || blocked(item, ItemAction.STORAGE)
                || (definition != null && definition.stopStorage())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (blocked(event.getItem(), ItemAction.INTERACT) || blocked(event.getItem(), ItemAction.INVENTORY_MOVE)
                || policyViolation(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        for (ItemStack item : event.getContents().getContents()) {
            if (potions.blocked(item) || blocked(item, ItemAction.CRAFT)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (BypassPolicy.bypasses(configs, event.getEnchanter(), "glitgcore.bypass.enchants")) return;
        ItemStack candidate = event.getItem().clone();
        event.getEnchantsToAdd().forEach((enchantment, level) -> candidate.addUnsafeEnchantment(enchantment, level));
        if (enchants.violation(candidate) != null) {
            event.setCancelled(true);
            messages.send(event.getEnchanter(), "enchant-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) { validatePrepared(event.getViewers(), event.getResult(), event::setResult); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) { validatePrepared(event.getViewers(), event.getResult(), event::setResult); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent event) { validatePrepared(event.getViewers(), event.getResult(), event::setResult); }

    private void validatePrepared(List<HumanEntity> viewers, ItemStack result, java.util.function.Consumer<ItemStack> setter) {
        Player player = firstPlayer(viewers);
        if (player != null && (bypass(player) || BypassPolicy.bypasses(configs, player, "glitgcore.bypass.enchants"))) return;
        if (result != null && (policyViolation(result) || blocked(result, ItemAction.CRAFT))) {
            setter.accept(null);
            if (player != null) messages.send(player, "enchant-blocked");
        }
    }

    private boolean blocked(ItemStack stack, ItemAction action) {
        return configs.enabled("item-rules") && stack != null && rules.blockedRule(stack, action) != null;
    }

    private boolean policyViolation(ItemStack stack) {
        return stack != null && ((configs.enabled("potion-policy") && potions.blocked(stack))
                || (configs.enabled("enchant-policy") && enchants.violation(stack) != null));
    }

    private boolean exceedsLimit(Player player, ItemStack incoming) {
        if (!configs.enabled("item-limits") || BypassPolicy.bypasses(configs, player, "glitgcore.bypass.itemlimits") || incoming == null) return false;
        RuleEngine.Limit limit = rules.matchingLimit(incoming);
        if (limit == null) return false;
        List<ItemStack> roots = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
        if (configs.main().getBoolean("items.include-ender-chest", false)) roots.addAll(Arrays.asList(player.getEnderChest().getContents()));
        int count = adapter.flatten(roots, 4, 2048).stream()
                .filter(descriptor -> new dev.glitg.core.domain.ItemMatcher().matches(limit.matcher(), descriptor))
                .mapToInt(dev.glitg.core.domain.ItemDescriptor::amount).sum();
        int addition = new dev.glitg.core.domain.ItemMatcher().matches(limit.matcher(), adapter.describe(incoming)) ? incoming.getAmount() : 0;
        return count + addition > limit.maximum();
    }

    private void tellBlocked(Player player, ItemStack item) {
        RuleEngine.Limit limit = rules.matchingLimit(item);
        if (limit != null) messages.send(player, "item-limit", Map.of("limit", limit.maximum()));
        else messages.send(player, "blocked-item");
    }

    private boolean bypass(Player player) { return BypassPolicy.bypasses(configs, player, "glitgcore.bypass.itemrules"); }

    private static Player firstPlayer(List<HumanEntity> viewers) {
        return viewers.stream().filter(Player.class::isInstance).map(Player.class::cast).findFirst().orElse(null);
    }

    private static ItemStack incomingToPlayer(InventoryClickEvent event, Player player) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return null;
        InventoryView view = event.getView();
        if (clicked == view.getTopInventory() && event.isShiftClick()) return event.getCurrentItem();
        if (clicked == player.getInventory() && event.getCursor().getType() != Material.AIR) return event.getCursor();
        return null;
    }
}
