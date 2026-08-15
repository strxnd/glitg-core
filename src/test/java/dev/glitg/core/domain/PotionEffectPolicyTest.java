package dev.glitg.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PotionEffectPolicyTest {
    @Test void appliesEffectAmplifierAndExactVariantDurationRules() {
        var policy = new PotionEffectPolicy(Set.of("poison"), Map.of("speed", 0), List.of(
                new PotionEffectPolicy.DurationRule("strength-two", "strength", 1, 9600, 9600)));
        assertNotNull(policy.validate(List.of(new PotionEffectPolicy.Effect("minecraft:poison", 0, 900))));
        assertNotNull(policy.validate(List.of(new PotionEffectPolicy.Effect("speed", 1, 1200))));
        assertNotNull(policy.validate(List.of(new PotionEffectPolicy.Effect("strength", 1, 1800))));
        assertNull(policy.validate(List.of(new PotionEffectPolicy.Effect("strength", 1, 9600))));
        assertNull(policy.validate(List.of(new PotionEffectPolicy.Effect("strength", 0, 9600))));
    }
}
