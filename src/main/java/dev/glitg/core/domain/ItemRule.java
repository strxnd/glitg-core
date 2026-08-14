package dev.glitg.core.domain;

import java.util.Map;
import java.util.Set;

public record ItemRule(
        String id,
        boolean enabled,
        Set<ItemAction> actions,
        String material,
        String potion,
        Integer customModelData,
        Map<String, String> persistentData,
        Map<String, Integer> enchantments,
        Set<String> requiredTags
) {
    public ItemRule {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("rule id is required");
        actions = Set.copyOf(actions == null || actions.isEmpty() ? Set.of(ItemAction.ALL) : actions);
        material = normalize(material);
        potion = normalize(potion);
        persistentData = Map.copyOf(persistentData == null ? Map.of() : persistentData);
        enchantments = Map.copyOf(enchantments == null ? Map.of() : enchantments);
        requiredTags = Set.copyOf(requiredTags == null ? Set.of() : requiredTags);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    public boolean appliesTo(ItemAction action) {
        return enabled && actions.stream().anyMatch(candidate -> candidate.covers(action));
    }
}
