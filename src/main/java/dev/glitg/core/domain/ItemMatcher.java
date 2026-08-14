package dev.glitg.core.domain;

import java.util.Map;

public final class ItemMatcher {
    public boolean matches(ItemRule rule, ItemDescriptor item) {
        if (!rule.enabled()) return false;
        if (rule.material() != null && !rule.material().equals(item.material())) return false;
        if (rule.potion() != null && !rule.potion().equals(item.potion())) return false;
        if (rule.customModelData() != null && !rule.customModelData().equals(item.customModelData())) return false;
        if (!containsEntries(item.persistentData(), rule.persistentData())) return false;
        if (!containsMinimumLevels(item.enchantments(), rule.enchantments())) return false;
        return item.tags().containsAll(rule.requiredTags());
    }

    private static <K, V> boolean containsEntries(Map<K, V> actual, Map<K, V> required) {
        return required.entrySet().stream().allMatch(entry -> entry.getValue().equals(actual.get(entry.getKey())));
    }

    private static boolean containsMinimumLevels(Map<String, Integer> actual, Map<String, Integer> required) {
        return required.entrySet().stream().allMatch(entry -> actual.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }
}
