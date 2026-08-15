package dev.glitg.core.item;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.domain.PotionEffectPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PotionPolicyService {
    private final ConfigService configs;
    private volatile Set<String> banned = Set.of();
    private volatile boolean banTierOne;
    private volatile boolean banTierTwo;
    private volatile PotionEffectPolicy effectPolicy = new PotionEffectPolicy(Set.of(), Map.of(), List.of());

    public PotionPolicyService(ConfigService configs) {
        this.configs = configs;
        reload();
    }

    public void reload() {
        var yaml = configs.file("items.yml");
        var normalized = new HashSet<String>();
        yaml.getStringList("potion-policy.banned").forEach(value -> normalized.add(value.toLowerCase(Locale.ROOT)));
        banned = Set.copyOf(normalized);
        banTierOne = yaml.getBoolean("potion-policy.ban-tier-1", false);
        banTierTwo = yaml.getBoolean("potion-policy.ban-tier-2", false);
        Set<String> bannedEffects = new HashSet<>(yaml.getStringList("potion-policy.banned-effects"));
        Map<String, Integer> maximumAmplifiers = new HashMap<>();
        ConfigurationSection maximums = yaml.getConfigurationSection("potion-policy.maximum-amplifier");
        if (maximums != null) maximums.getKeys(false).forEach(key -> maximumAmplifiers.put(key, maximums.getInt(key)));
        List<PotionEffectPolicy.DurationRule> durationRules = new ArrayList<>();
        ConfigurationSection durations = yaml.getConfigurationSection("potion-policy.duration-rules");
        if (durations != null) {
            for (String id : durations.getKeys(false)) {
                ConfigurationSection rule = durations.getConfigurationSection(id);
                if (rule == null || !rule.getBoolean("enabled", true)) continue;
                String effect = rule.getString("effect");
                if (effect == null || effect.isBlank()) throw new IllegalArgumentException("potion duration rule " + id + " requires an effect");
                Integer amplifier = rule.contains("amplifier") ? rule.getInt("amplifier") : null;
                durationRules.add(new PotionEffectPolicy.DurationRule(id, effect, amplifier,
                        rule.getInt("minimum-duration-ticks", 0), rule.getInt("maximum-duration-ticks", Integer.MAX_VALUE)));
            }
        }
        effectPolicy = new PotionEffectPolicy(bannedEffects, maximumAmplifiers, durationRules);
    }

    public boolean blocked(ItemStack stack) {
        if (stack == null || !(stack.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.getBasePotionType() != null && banned.contains(meta.getBasePotionType().getKey().asString().toLowerCase(Locale.ROOT))) return true;
        var effects = new ArrayList<PotionEffect>();
        if (meta.getBasePotionType() != null) effects.addAll(meta.getBasePotionType().getPotionEffects());
        effects.addAll(meta.getCustomEffects());
        int tier = effects.stream().mapToInt(effect -> effect.getAmplifier() + 1).max().orElse(1);
        if ((tier <= 1 && banTierOne) || (tier >= 2 && banTierTwo)) return true;
        List<PotionEffectPolicy.Effect> descriptors = effects.stream().map(effect -> new PotionEffectPolicy.Effect(
                effect.getType().getKey().asString(), effect.getAmplifier(), effect.getDuration())).toList();
        return effectPolicy.validate(descriptors) != null;
    }
}
