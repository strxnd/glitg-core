package dev.glitg.core.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure effect-level potion validation, independent of Bukkit item forms. */
public final class PotionEffectPolicy {
    private final Set<String> bannedEffects;
    private final Map<String, Integer> maximumAmplifiers;
    private final List<DurationRule> durationRules;

    public PotionEffectPolicy(Set<String> bannedEffects, Map<String, Integer> maximumAmplifiers,
                              List<DurationRule> durationRules) {
        this.bannedEffects = bannedEffects.stream().map(PotionEffectPolicy::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.maximumAmplifiers = maximumAmplifiers.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> normalize(entry.getKey()), Map.Entry::getValue));
        this.durationRules = List.copyOf(durationRules);
        if (this.maximumAmplifiers.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("maximum potion amplifiers cannot be negative");
        }
    }

    public Violation validate(List<Effect> effects) {
        for (Effect effect : effects) {
            String key = normalize(effect.key());
            if (bannedEffects.contains(key)) return new Violation(key, Reason.BANNED_EFFECT);
            Integer maximum = maximumAmplifiers.get(key);
            if (maximum != null && effect.amplifier() > maximum) {
                return new Violation(key, Reason.ABOVE_MAXIMUM_AMPLIFIER);
            }
            for (DurationRule rule : durationRules) {
                if (!rule.matches(key, effect.amplifier())) continue;
                if (effect.durationTicks() < rule.minimumDurationTicks()
                        || effect.durationTicks() > rule.maximumDurationTicks()) {
                    return new Violation(key, Reason.INVALID_DURATION);
                }
            }
        }
        return null;
    }

    private static String normalize(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    public record Effect(String key, int amplifier, int durationTicks) {
        public Effect {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("effect key is required");
            if (amplifier < 0 || durationTicks < 0) throw new IllegalArgumentException("effect values cannot be negative");
        }
    }

    public record DurationRule(String id, String effect, Integer amplifier,
                               int minimumDurationTicks, int maximumDurationTicks) {
        public DurationRule {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("duration rule id is required");
            effect = normalize(effect);
            if (amplifier != null && amplifier < 0) throw new IllegalArgumentException("amplifier cannot be negative");
            if (minimumDurationTicks < 0 || maximumDurationTicks < minimumDurationTicks) {
                throw new IllegalArgumentException("invalid potion duration range");
            }
        }

        boolean matches(String actualEffect, int actualAmplifier) {
            return effect.equals(actualEffect) && (amplifier == null || amplifier == actualAmplifier);
        }
    }

    public enum Reason { BANNED_EFFECT, ABOVE_MAXIMUM_AMPLIFIER, INVALID_DURATION }
    public record Violation(String effect, Reason reason) {}
}
