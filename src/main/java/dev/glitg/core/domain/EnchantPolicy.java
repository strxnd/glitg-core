package dev.glitg.core.domain;

import java.util.Map;
import java.util.Set;

public final class EnchantPolicy {
    private final Set<String> banned;
    private final Map<String, Integer> maximumLevels;
    private final Set<String> exemptMaterials;

    public EnchantPolicy(Set<String> banned, Map<String, Integer> maximumLevels, Set<String> exemptMaterials) {
        this.banned = normalize(banned);
        this.maximumLevels = maximumLevels.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
        this.exemptMaterials = normalize(exemptMaterials);
        if (this.maximumLevels.values().stream().anyMatch(level -> level < 0)) {
            throw new IllegalArgumentException("enchantment maximum levels cannot be negative");
        }
    }

    public Violation validate(ItemDescriptor item) {
        if (exemptMaterials.contains(item.material())) return null;
        for (var entry : item.enchantments().entrySet()) {
            String enchant = entry.getKey().toLowerCase();
            if (banned.contains(enchant)) return new Violation(enchant, entry.getValue(), 0, Reason.BANNED);
            Integer maximum = maximumLevels.get(enchant);
            if (maximum != null && entry.getValue() > maximum) {
                return new Violation(enchant, entry.getValue(), maximum, Reason.ABOVE_MAXIMUM);
            }
        }
        return null;
    }

    private static Set<String> normalize(Set<String> values) {
        return values.stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public enum Reason { BANNED, ABOVE_MAXIMUM }
    public record Violation(String enchantment, int actualLevel, int maximumLevel, Reason reason) {}
}
