package dev.glitg.core.item;

import dev.glitg.core.config.ConfigService;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PotionPolicyService {
    private final ConfigService configs;
    private volatile Set<String> banned = Set.of();
    private volatile boolean banTierOne;
    private volatile boolean banTierTwo;

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
    }

    public boolean blocked(ItemStack stack) {
        if (stack == null || !(stack.getItemMeta() instanceof PotionMeta meta)) return false;
        if (meta.getBasePotionType() != null && banned.contains(meta.getBasePotionType().getKey().asString().toLowerCase(Locale.ROOT))) return true;
        var effects = new java.util.ArrayList<PotionEffect>();
        if (meta.getBasePotionType() != null) effects.addAll(meta.getBasePotionType().getPotionEffects());
        effects.addAll(meta.getCustomEffects());
        int tier = effects.stream().mapToInt(effect -> effect.getAmplifier() + 1).max().orElse(1);
        return (tier <= 1 && banTierOne) || (tier >= 2 && banTierTwo);
    }
}
