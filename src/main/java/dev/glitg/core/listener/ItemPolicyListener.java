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
import dev.glitg.core.persistence.SqliteDatabase;
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
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;
import java.time.Clock;

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
    private final SqliteDatabase database;
    private final Clock clock;
    private final NamespacedKey grandfatheredKey;

    public ItemPolicyListener(JavaPlugin plugin, ConfigService configs, MessageService messages, RuleEngine rules,
                              EnchantPolicyService enchants, PotionPolicyService potions, BukkitItemAdapter adapter,
                              CombatTagService combat, PostDeathProtectionService postDeath, SqliteDatabase database,
                              Clock clock) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.rules = rules;
        this.enchants = enchants;
        this.potions = potions;
        this.adapter = adapter;
        this.combat = combat;
        this.postDeath = postDeath;
        this.database = database;
        this.clock = clock;
        this.grandfatheredKey = new NamespacedKey(plugin, "grandfathered_policy_v1");
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (configs.main().getBoolean("items.audit-insertions", false)) {
                plugin.getServer().getOnlinePlayers().forEach(this::auditPlayer);
            }
        }, 100L, Math.max(20L, configs.main().getLong("items.audit-interval-ticks", 100L)));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        grandfatherExisting(event.getPlayer());
        if (!configs.main().getBoolean("items.audit-insertions", false)) return;
        event.getPlayer().getScheduler().runDelayed(plugin, task -> auditPlayer(event.getPlayer()), null, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (protectedPlayer(player)
                && configs.main().getBoolean("protections.post-death.block-item-pickup", true)) {
            event.setCancelled(true);
            messages.send(player, "post-death-blocked");
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (blocked(player, item, ItemAction.PICKUP) || policyViolation(player, item) || exceedsLimit(player, item)) {
            event.setCancelled(true);
            tellBlocked(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (blocked(event.getPlayer(), item, ItemAction.DROPPING)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "blocked-item");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (blocked(event.getPlayer(), item, ItemAction.INTERACT) || policyViolation(event.getPlayer(), item)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "blocked-item");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (blocked(player, result, ItemAction.CRAFT) || exceedsLimit(player, result) || policyViolation(player, result)) {
            event.setCancelled(true);
            tellBlocked(player, result);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;
        Player player = firstPlayer(event.getViewers());
        if (blocked(player, result, ItemAction.CRAFT) || policyViolation(player, result)) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (protectedPlayer(player)
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
            if (blocked(player, item, ItemAction.INVENTORY_MOVE) || (storage && blocked(player, item, ItemAction.STORAGE))
                    || (trade && blocked(player, item, ItemAction.TRADE)) || (equip && blocked(player, item, ItemAction.EQUIP))
                    || (storage && protectedStorageBlocked(player, item)) || policyViolation(player, item)) {
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
        if (!(event.getPlayer() instanceof Player player)) return;
        if (protectedPlayer(player)
                && configs.main().getBoolean("protections.post-death.block-container-access", true)
                && event.getInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
            messages.send(player, "post-death-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getOldCursor();
        boolean touchesStorage = event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())
                && event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
        if (touchesStorage && protectedPlayer(player)
                && configs.main().getBoolean("protections.post-death.block-container-access", true)) {
            event.setCancelled(true);
            messages.send(player, "post-death-blocked");
            return;
        }
        if (blocked(player, item, ItemAction.INVENTORY_MOVE) || (touchesStorage && blocked(player, item, ItemAction.STORAGE))
                || (touchesStorage && protectedStorageBlocked(player, item)) || policyViolation(player, item)
                || exceedsLimit(player, item) || (touchesStorage && exceedsStorageLimit(event.getView().getTopInventory(), item))) {
            event.setCancelled(true);
            tellBlocked(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (blocked(null, item, ItemAction.INVENTORY_MOVE) || blocked(null, item, ItemAction.STORAGE)
                || exceedsStorageLimit(event.getDestination(), item)
                || protectedStorageBlocked(null, item) || policyViolation(null, item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityStorage(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame) && !(event.getRightClicked() instanceof ArmorStand)) return;
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        ItemAction action = event.getRightClicked() instanceof ItemFrame ? ItemAction.STORAGE : ItemAction.EQUIP;
        if (blocked(event.getPlayer(), item, action) || blocked(event.getPlayer(), item, ItemAction.INVENTORY_MOVE)
                || protectedStorageBlocked(event.getPlayer(), item) || policyViolation(event.getPlayer(), item)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "blocked-item");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (blocked(null, event.getItem(), ItemAction.INTERACT) || blocked(null, event.getItem(), ItemAction.INVENTORY_MOVE)
                || policyViolation(null, event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        for (ItemStack item : event.getContents().getContents()) {
            if (policyViolation(null, item) || blocked(null, item, ItemAction.CRAFT)) {
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
        if (result != null && (policyViolation(player, result) || blocked(player, result, ItemAction.CRAFT))) {
            setter.accept(null);
            if (player != null) messages.send(player, "enchant-blocked");
        }
    }

    private boolean blocked(Player player, ItemStack stack, ItemAction action) {
        if (!configs.enabled("item-rules") || (player != null && BypassPolicy.bypasses(configs, player, "glitgcore.bypass.itemrules"))) return false;
        return deepStacks(stack).stream().anyMatch(item -> rules.blockedRule(item, action) != null);
    }

    private boolean policyViolation(Player player, ItemStack stack) {
        boolean potionBypass = player != null && BypassPolicy.bypasses(configs, player, "glitgcore.bypass.potions");
        boolean enchantBypass = player != null && BypassPolicy.bypasses(configs, player, "glitgcore.bypass.enchants");
        return deepStacks(stack).stream().filter(item -> !isGrandfathered(item)).anyMatch(item -> (configs.enabled("potion-policy") && !potionBypass && potions.blocked(item))
                || (configs.enabled("enchant-policy") && !enchantBypass && enchants.violation(item) != null));
    }

    private boolean exceedsLimit(Player player, ItemStack incoming) {
        if (!configs.enabled("item-limits") || BypassPolicy.bypasses(configs, player, "glitgcore.bypass.itemlimits") || incoming == null) return false;
        List<ItemStack> roots = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
        if (configs.main().getBoolean("items.include-ender-chest", false)) roots.addAll(Arrays.asList(player.getEnderChest().getContents()));
        for (ItemStack candidate : deepStacks(incoming)) {
            for (RuleEngine.Limit limit : rules.matchingLimits(candidate)) {
                if (limit.scope() == ItemLimitScope.CARRIED
                        || (limit.scope() == ItemLimitScope.COMBAT_LOADOUT && combat.isTagged(player.getUniqueId()))) {
                    if (exceeds(limit, roots, candidate)) return true;
                }
            }
        }
        return false;
    }

    private boolean exceedsStorageLimit(Inventory destination, ItemStack incoming) {
        if (!configs.enabled("item-limits") || incoming == null) return false;
        List<ItemStack> roots = new ArrayList<>(Arrays.asList(destination.getContents()));
        return deepStacks(incoming).stream().anyMatch(candidate -> rules.matchingLimits(candidate).stream()
                .filter(limit -> limit.scope() == ItemLimitScope.STORED)
                .anyMatch(limit -> exceeds(limit, roots, candidate)));
    }

    private boolean exceeds(RuleEngine.Limit limit, List<ItemStack> roots, ItemStack incoming) {
        List<RuleEngine.Limit> group = rules.limitGroup(limit);
        return !new dev.glitg.core.domain.ItemLimitCalculator(new dev.glitg.core.domain.ItemMatcher())
                .evaluateGroup(group.stream().map(RuleEngine.Limit::matcher).toList(), limit.maximumFor(incoming),
                        adapter.flatten(roots, 4, 2048), adapter.describe(incoming)).allowed();
    }

    private void tellBlocked(Player player, ItemStack item) {
        RuleEngine.Limit limit = deepStacks(item).stream().map(rules::matchingLimit).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (limit != null) messages.send(player, "item-limit", Map.of("limit", limit.maximumFor(item)));
        else messages.send(player, "blocked-item");
    }

    private void auditPlayer(Player player) {
        if (!player.isOnline()) return;
        boolean changed = false;
        ItemStack[] contents = player.getInventory().getContents();
        Map<String, Integer> groupCounts = new java.util.HashMap<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) continue;
            if (blocked(player, item, ItemAction.INVENTORY_MOVE) || policyViolation(player, item)) {
                player.getInventory().setItem(slot, null);
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                changed = true;
                continue;
            }
            boolean excess = false;
            Map<String, Integer> additions = new java.util.HashMap<>();
            Map<String, Integer> maximums = new java.util.HashMap<>();
            if (!BypassPolicy.bypasses(configs, player, "glitgcore.bypass.itemlimits")) for (ItemStack nested : deepStacks(item)) {
                for (RuleEngine.Limit limit : rules.matchingLimits(nested)) {
                    if (limit.scope() != ItemLimitScope.CARRIED
                            && !(limit.scope() == ItemLimitScope.COMBAT_LOADOUT && combat.isTagged(player.getUniqueId()))) continue;
                    additions.merge(limit.group(), nested.getAmount(), Integer::sum);
                    maximums.merge(limit.group(), limit.maximumFor(nested), Math::min);
                    break;
                }
            }
            for (var addition : additions.entrySet()) {
                if (groupCounts.getOrDefault(addition.getKey(), 0) + addition.getValue() > maximums.get(addition.getKey())) {
                    excess = true;
                    break;
                }
            }
            if (excess) {
                player.getInventory().setItem(slot, null);
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                changed = true;
            } else {
                additions.forEach((group, amount) -> groupCounts.merge(group, amount, Integer::sum));
            }
        }
        if (changed) messages.send(player, "item-audit");
    }

    private boolean protectedPlayer(Player player) {
        return configs.enabled("protections") && !BypassPolicy.bypasses(configs, player, "glitgcore.bypass.protection")
                && postDeath.isProtected(player.getUniqueId());
    }

    private boolean protectedStorageBlocked(Player player, ItemStack stack) {
        if (!configs.enabled("protected-items") || (player != null
                && BypassPolicy.bypasses(configs, player, "glitgcore.bypass.protecteditems"))) return false;
        return deepStacks(stack).stream().map(rules::protectedDefinition)
                .anyMatch(definition -> definition != null && definition.stopStorage());
    }

    private List<ItemStack> deepStacks(ItemStack root) {
        if (root == null || root.getType().isAir()) return List.of();
        record Pending(ItemStack item, int depth) {}
        var result = new ArrayList<ItemStack>();
        var queue = new java.util.ArrayDeque<Pending>();
        queue.add(new Pending(root, 0));
        while (!queue.isEmpty()) {
            Pending pending = queue.removeFirst();
            if (result.size() >= 4096) throw new IllegalStateException("nested item traversal exceeded 4096 items");
            result.add(pending.item());
            if (pending.depth() >= 8) continue;
            boolean bundle = pending.item().getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta;
            boolean shulker = pending.item().getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta;
            if ((bundle && !configs.main().getBoolean("items.traverse-bundles", true))
                    || (shulker && !configs.main().getBoolean("items.traverse-shulkers", true))) continue;
            adapter.nestedContents(pending.item()).forEach(item -> queue.addLast(new Pending(item, pending.depth() + 1)));
        }
        return List.copyOf(result);
    }

    private void grandfatherExisting(Player player) {
        if (!configs.main().getBoolean("items.grandfather-existing-mythical", true)) return;
        try {
            if (!database.claimPolicyMigration(player.getUniqueId(), "mythical-v1", clock.millis())) return;
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack updated = markGrandfathered(contents[slot], 0);
                if (updated != contents[slot]) player.getInventory().setItem(slot, updated);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not persist item-policy migration for " + player.getName() + ": " + exception.getMessage());
        }
    }

    private ItemStack markGrandfathered(ItemStack source, int depth) {
        if (source == null || source.getType().isAir() || depth > 8) return source;
        ItemStack item = source;
        boolean changed = false;
        if (potions.blocked(item) || enchants.violation(item) != null) {
            item = item.clone();
            item.editMeta(meta -> meta.getPersistentDataContainer().set(grandfatheredKey, PersistentDataType.BYTE, (byte) 1));
            changed = true;
        }
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundle) {
            List<ItemStack> existing = bundle.getItems();
            List<ItemStack> updated = existing.stream().map(nested -> markGrandfathered(nested, depth + 1)).toList();
            if (!updated.equals(existing)) {
                if (!changed) item = item.clone();
                org.bukkit.inventory.meta.BundleMeta mutable = (org.bukkit.inventory.meta.BundleMeta) item.getItemMeta();
                mutable.setItems(updated);
                item.setItemMeta(mutable);
                changed = true;
            }
        } else if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta stateMeta
                && stateMeta.getBlockState() instanceof org.bukkit.block.ShulkerBox shulker) {
            ItemStack[] existing = shulker.getInventory().getContents();
            ItemStack[] updated = existing.clone();
            boolean nestedChanged = false;
            for (int slot = 0; slot < existing.length; slot++) {
                updated[slot] = markGrandfathered(existing[slot], depth + 1);
                nestedChanged |= updated[slot] != existing[slot];
            }
            if (nestedChanged) {
                if (!changed) item = item.clone();
                org.bukkit.inventory.meta.BlockStateMeta mutable = (org.bukkit.inventory.meta.BlockStateMeta) item.getItemMeta();
                org.bukkit.block.ShulkerBox mutableBox = (org.bukkit.block.ShulkerBox) mutable.getBlockState();
                mutableBox.getInventory().setContents(updated);
                mutable.setBlockState(mutableBox);
                item.setItemMeta(mutable);
                changed = true;
            }
        }
        return changed ? item : source;
    }

    private boolean isGrandfathered(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(grandfatheredKey, PersistentDataType.BYTE);
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
