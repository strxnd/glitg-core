package dev.glitg.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;

public final class ConfigService {
    public static final int CURRENT_VERSION = 1;
    private static final List<String> FILES = List.of(
            "config.yml", "messages.yml", "items.yml", "enchants.yml", "recipes.yml", "rituals.yml", "kits.yml");

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> configurations = new LinkedHashMap<>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() throws ConfigurationException {
        plugin.getDataFolder().mkdirs();
        for (String name : FILES) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) plugin.saveResource(name, false);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                throw new ConfigurationException(name + " is not valid YAML: " + exception.getMessage());
            }
            validateVersion(name, yaml);
            configurations.put(name, yaml);
        }
        var flattened = new LinkedHashMap<String, Object>();
        configurations.get("config.yml").getValues(true).forEach(flattened::put);
        List<String> errors = new ConfigValidator().validate(flattened);
        if (!errors.isEmpty()) throw new ConfigurationException(String.join("; ", errors));
        validateDurations(configurations.get("config.yml"));
    }

    public void reload() throws ConfigurationException {
        Map<String, YamlConfiguration> previous = new LinkedHashMap<>(configurations);
        configurations.clear();
        try {
            load();
        } catch (ConfigurationException exception) {
            configurations.clear();
            configurations.putAll(previous);
            throw exception;
        }
    }

    private void validateDurations(YamlConfiguration config) throws ConfigurationException {
        var cooldowns = config.getConfigurationSection("cooldowns");
        if (cooldowns == null) throw new ConfigurationException("cooldowns must be a YAML section");
        for (String key : cooldowns.getKeys(false)) {
            try {
                DurationParser.parse(config.get("cooldowns." + key));
            } catch (IllegalArgumentException exception) {
                throw new ConfigurationException("cooldowns." + key + ": " + exception.getMessage());
            }
        }
    }

    private static void validateVersion(String name, YamlConfiguration yaml) throws ConfigurationException {
        Object version = yaml.get("config-version");
        if (!(version instanceof Number number) || number.intValue() != CURRENT_VERSION) {
            throw new ConfigurationException(name + " must use config-version " + CURRENT_VERSION
                    + "; configuration upgrades are not supported");
        }
    }

    public YamlConfiguration main() { return configurations.get("config.yml"); }
    public YamlConfiguration file(String name) { return configurations.get(name); }

    public boolean enabled(String feature) {
        return main().getBoolean("features." + feature, false);
    }

    public void setFeature(String feature, boolean value) throws IOException {
        YamlConfiguration main = main();
        main.set("features." + feature, value);
        main.save(new File(plugin.getDataFolder(), "config.yml"));
    }

    public void save(String name) throws IOException {
        file(name).save(new File(plugin.getDataFolder(), name));
    }
}
