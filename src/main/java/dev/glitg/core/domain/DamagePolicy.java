package dev.glitg.core.domain;

import java.util.Map;

public final class DamagePolicy {
    private final Map<String, Double> caps;

    public DamagePolicy(Map<String, Double> caps) {
        this.caps = caps.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> entry.getKey().toLowerCase(), Map.Entry::getValue));
        if (this.caps.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException("damage caps must be finite and non-negative");
        }
    }

    public double cap(String source, double finalDamage) {
        if (!Double.isFinite(finalDamage) || finalDamage < 0) throw new IllegalArgumentException("invalid damage");
        Double maximum = caps.get(source.toLowerCase());
        return maximum == null ? finalDamage : Math.min(finalDamage, maximum);
    }
}
