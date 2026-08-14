package dev.glitg.core.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ItemDescriptor(
        String material,
        String potion,
        Integer customModelData,
        Map<String, String> persistentData,
        Map<String, Integer> enchantments,
        Set<String> tags,
        int amount
) {
    public ItemDescriptor {
        material = normalize(material);
        potion = potion == null ? null : normalize(potion);
        persistentData = Map.copyOf(persistentData == null ? Map.of() : persistentData);
        enchantments = Map.copyOf(enchantments == null ? Map.of() : enchantments);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "material").trim().toLowerCase();
    }
}
