package dev.glitg.core.item;

import dev.glitg.core.domain.ItemDescriptor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BukkitItemAdapter {
    public ItemDescriptor describe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return new ItemDescriptor("air", null, null, Map.of(), Map.of(), Set.of(), 0);
        }
        ItemMeta meta = stack.getItemMeta();
        String potion = null;
        if (meta instanceof PotionMeta potionMeta && potionMeta.getBasePotionType() != null) {
            potion = potionMeta.getBasePotionType().getKey().asString();
        }
        Integer customModelData = null;
        Map<String, String> pdc = new HashMap<>();
        Set<String> tags = new HashSet<>();
        if (meta.hasCustomModelDataComponent()) {
            var component = meta.getCustomModelDataComponent();
            for (Float value : component.getFloats()) {
                tags.add("cmd-float:" + value);
                if (customModelData == null && value == Math.rint(value)) customModelData = value.intValue();
            }
            component.getStrings().forEach(value -> tags.add("cmd-string:" + value));
            component.getFlags().forEach(value -> tags.add("cmd-flag:" + value));
            component.getColors().forEach(value -> tags.add("cmd-color:" + value.asRGB()));
        }
        for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
            tags.add("pdc:" + key.asString());
            String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (value != null) pdc.put(key.asString(), value);
        }
        Map<String, Integer> enchantments = new HashMap<>();
        stack.getEnchantments().forEach((enchant, level) -> enchantments.put(key(enchant), level));
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storage) {
            storage.getStoredEnchants().forEach((enchant, level) -> enchantments.put(key(enchant), level));
        }
        return new ItemDescriptor(stack.getType().getKey().asString(), potion, customModelData, pdc, enchantments, tags, stack.getAmount());
    }

    public List<ItemStack> nestedContents(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return List.of();
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta bundle) return List.copyOf(bundle.getItems());
        if (meta instanceof BlockStateMeta blockState && blockState.getBlockState() instanceof ShulkerBox shulker) {
            var items = new ArrayList<ItemStack>();
            for (ItemStack nested : shulker.getInventory().getContents()) {
                if (nested != null && !nested.getType().isAir()) items.add(nested.clone());
            }
            return List.copyOf(items);
        }
        return List.of();
    }

    public List<ItemDescriptor> flatten(List<ItemStack> roots, int maxDepth, int maxNodes) {
        record Pending(ItemStack stack, int depth) {}
        var result = new ArrayList<ItemDescriptor>();
        var queue = new java.util.ArrayDeque<Pending>();
        roots.stream().filter(java.util.Objects::nonNull).forEach(stack -> queue.add(new Pending(stack, 0)));
        while (!queue.isEmpty()) {
            Pending pending = queue.removeFirst();
            if (result.size() >= maxNodes) throw new IllegalStateException("nested item traversal exceeded " + maxNodes + " items");
            result.add(describe(pending.stack()));
            if (pending.depth() < maxDepth) {
                nestedContents(pending.stack()).forEach(nested -> queue.addLast(new Pending(nested, pending.depth() + 1)));
            }
        }
        return List.copyOf(result);
    }

    private static String key(Enchantment enchantment) {
        return enchantment.getKey().asString();
    }
}
