package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.domain.ItemAction;
import dev.glitg.core.domain.CombatTagService;
import dev.glitg.core.domain.ItemLimitScope;
import dev.glitg.core.item.BukkitItemAdapter;
import dev.glitg.core.item.EnchantPolicyService;
import dev.glitg.core.item.PotionPolicyService;
import dev.glitg.core.item.RuleEngine;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.permission.BypassPolicy;
import dev.glitg.core.service.PostDeathProtectionService;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private final CombatTagService combat;
    private final PostDeathProtectionService postDeath;
    private final JavaPlugin plugin;

    public ItemPolicyListener(JavaPlugin plugin, ConfigService configs, MessageService messages, RuleEngine rules,
                              EnchantPolicyService enchants, PotionPolicyService potions, BukkitItemAdapter adapter,
                              CombatTagService combat, PostDeathProtectionService postDeath) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.rules = rules;
        this.enchants = enchants;
        this.potions = potions;
        this.adapter = adapter;
        this.combat = combat;
        this.postDeath = postDeath;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (configs.main().getBoolean("items.audit-insertions", false)) {
                plugin.getServer().getOnlinePlayers().forEach(this::auditPlayer);
            }
        }, 100L, Math.max(20L, configs.main().getLong("items.audit-interval-ticks", 100L)));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        if (!configs.main().getBoolean("items.audit-insertions", false)) return;
        event.getPlayer().getScheduler().runDelayed(plugin, task -> auditPlayer(event.getPlayer()), null, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || bypass(player)) return;
        if (postDeath.isProtected(player.getUniqueId())
                && configs.main().getBoolean("protections.post-death.block-item-pickup", true)) {
            event.setCancelled(true);
            messages.send(player, "post-death-blocked");
            return;
        }
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
        if (postDeath.isProtected(player.getUniqueId())
                && configs.main().getBoolean("protections.post-death.block-container-access", true)
                && event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
            messages.send(player, "post-death-blocked");
            return;
        }
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
            return;
        }
        ItemStack stored = incomingToStorage(event, player);
        if (stored != null && exceedsStorageLimit(event.getView().getTopInventory(), stored)) {
            event.setCancelled(true);
            tellBlocked(player, stored);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || bypass(player)) return;
        if (postDeath.isProtected(player.getUniqueId())
                && configs.main().getBoolean("protections.post-death.block-container-access", true)
                && event.getInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
            messages.send(player, "post-death-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || bypass(player)) return;
        ItemStack item = event.getOldCursor();
        boolean touchesStorage = event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())
                && event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
        if (touchesStorage && postDeath.isProtected(player.getUniqueId())
                && configs.main().getBoolean("protections.post-death.block-container-access", true)) {
            event.setCancelled(true);
            messages.send(player, "post-death-blocked");
            return;
        }
        RuleEngine.ProtectedDefinition definition = rules.protectedDefinition(item);
        if (blocked(item, ItemAction.INVENTORY_MOVE) || (touchesStorage && blocked(item, ItemAction.STORAGE))
                || (touchesStorage && definition != null && definition.stopStorage()) || policyViolation(item)
                || exceedsLimit(player, item) || (touchesStorage && exceedsStorageLimit(event.getView().getTopInventory(), item))) {
            event.setCancelled(true);
            tellBlocked(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        RuleEngine.ProtectedDefinition definition = rules.protectedDefinition(item);
        if (blocked(item, ItemAction.INVENTORY_MOVE) || blocked(item, ItemAction.STORAGE)
                || exceedsStorageLimit(event.getDestination(), item)
                || (definition != null && definition.stopStorage())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityStorage(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame) && !(event.getRightClicked() instanceof ArmorStand)) return;
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        ItemAction action = event.getRightClicked() instanceof ItemFrame ? ItemAction.STORAGE : ItemAction.EQUIP;
        RuleEngine.ProtectedDefinition definition = rules.protectedDefinition(item);
        if (blocked(item, action) || blocked(item, ItemAction.INVENTORY_MOVE)
                || (definition != null && definition.stopStorage()) || policyViolation(item)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "blocked-item");
        }
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
        List<RuleEngine.Limit> matching = rules.matchingLimits(incoming).stream()
                .filter(limit -> limit.scope() == ItemLimitScope.CARRIED
                        || (limit.scope() == ItemLimitScope.COMBAT_LOADOUT && combat.isTagged(player.getUniqueId())))
                .toList();
        if (matching.isEmpty()) return false;
        List<ItemStack> roots = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
        if (configs.main().getBoolean("items.include-ender-chest", false)) roots.addAll(Arrays.asList(player.getEnderChest().getContents()));
        return matching.stream().anyMatch(limit -> exceeds(limit, roots, incoming));
    }

    private boolean exceedsStorageLimit(Inventory destination, ItemStack incoming) {
        if (!configs.enabled("item-limits") || incoming == null) return false;
        List<RuleEngine.Limit> matching = rules.matchingLimits(incoming).stream()
                .filter(limit -> limit.scope() == ItemLimitScope.STORED).toList();
        if (matching.isEmpty()) return false;
        List<ItemStack> roots = new ArrayList<>(Arrays.asList(destination.getContents()));
        return matching.stream().anyMatch(limit -> exceeds(limit, roots, incoming));
    }

    private boolean exceeds(RuleEngine.Limit limit, List<ItemStack> roots, ItemStack incoming) {
        List<RuleEngine.Limit> group = rules.limitGroup(limit);
        return !new dev.glitg.core.domain.ItemLimitCalculator(new dev.glitg.core.domain.ItemMatcher())
                .evaluateGroup(group.stream().map(RuleEngine.Limit::matcher).toList(), limit.maximumFor(incoming),
                        adapter.flatten(roots, 4, 2048), adapter.describe(incoming)).allowed();
    }

    private void tellBlocked(Player player, ItemStack item) {
        RuleEngine.Limit limit = rules.matchingLimit(item);
        if (limit != null) messages.send(player, "item-limit", Map.of("limit", limit.maximumFor(item)));
        else messages.send(player, "blocked-item");
    }

    private boolean bypass(Player player) { return BypassPolicy.bypasses(configs, player, "glitgcore.bypass.itemrules"); }

    private void auditPlayer(Player player) {
        if (!player.isOnline() || bypass(player)) return;
        boolean changed = false;
        ItemStack[] contents = player.getInventory().getContents();
        Map<String, Integer> groupCounts = new java.util.HashMap<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;
            if (blocked(item, ItemAction.INVENTORY_MOVE) || policyViolation(item)) {
                player.getInventory().setItem(slot, null);
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                changed = true;
                continue;
            }
            for (RuleEngine.Limit limit : rules.matchingLimits(item)) {
                if (limit.scope() != ItemLimitScope.CARRIED
                        && !(limit.scope() == ItemLimitScope.COMBAT_LOADOUT && combat.isTagged(player.getUniqueId()))) continue;
                int count = groupCounts.getOrDefault(limit.group(), 0);
                int maximum = limit.maximumFor(item);
                int allowed = Math.max(0, maximum - count);
                if (item.getAmount() > allowed) {
                    ItemStack overflow = item.clone();
                    overflow.setAmount(item.getAmount() - allowed);
                    if (allowed == 0) player.getInventory().setItem(slot, null); else item.setAmount(allowed);
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                    changed = true;
                }
                groupCounts.put(limit.group(), count + Math.min(item.getAmount(), allowed));
                break;
            }
        }
        if (changed) messages.send(player, "item-audit");
    }

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

    private static ItemStack incomingToStorage(InventoryClickEvent event, Player player) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || event.getView().getTopInventory().getType() == InventoryType.CRAFTING) return null;
        if (clicked == player.getInventory() && event.isShiftClick()) return event.getCurrentItem();
        if (clicked == event.getView().getTopInventory() && !event.getCursor().getType().isAir()) return event.getCursor();
        return null;
    }
}
