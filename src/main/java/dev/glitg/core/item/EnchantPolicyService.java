package dev.glitg.core.item;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.domain.EnchantPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class EnchantPolicyService {
    private final ConfigService configs;
    private final BukkitItemAdapter adapter;
    private volatile EnchantPolicy policy;

    public EnchantPolicyService(ConfigService configs, BukkitItemAdapter adapter) {
        this.configs = configs;
        this.adapter = adapter;
        reload();
    }

    public void reload() {
        var yaml = configs.file("enchants.yml");
        Map<String, Integer> maximums = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("maximum-levels");
        if (section != null) section.getKeys(false).forEach(key -> maximums.put(key, section.getInt(key)));
        policy = new EnchantPolicy(new HashSet<>(yaml.getStringList("banned")), maximums,
                new HashSet<>(yaml.getStringList("exempt-materials")));
    }

    public EnchantPolicy.Violation violation(ItemStack item) {
        return item == null ? null : policy.validate(adapter.describe(item));
    }
}
