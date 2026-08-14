package dev.glitg.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;

public final class ConfigService {
    public static final int CURRENT_VERSION = 2;
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
            migrate(name, file, yaml);
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

    private void migrate(String name, File file, YamlConfiguration yaml) throws ConfigurationException {
        int version = yaml.getInt("config-version", name.equals("config.yml") ? 0 : CURRENT_VERSION);
        if (version > CURRENT_VERSION) {
            throw new ConfigurationException(name + " uses future config-version " + version);
        }
        if (version == CURRENT_VERSION) return;
        try {
            File backup = new File(file.getParentFile(), name + ".backup-" + Instant.now().toEpochMilli());
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            if (name.equals("config.yml") && version < 1 && yaml.contains("rules.combat_time") && !yaml.contains("combat.duration-seconds")) {
                yaml.set("combat.duration-seconds", yaml.get("rules.combat_time"));
                yaml.set("rules.combat_time", null);
            }
            if (name.equals("config.yml") && version < 2 && !yaml.contains("features.operator-bypass")) {
                yaml.set("features.operator-bypass", false);
            }
            yaml.set("config-version", CURRENT_VERSION);
            yaml.save(file);
            plugin.getLogger().info("Migrated " + name + " to schema " + CURRENT_VERSION + " (backup: " + backup.getName() + ")");
        } catch (IOException exception) {
            throw new ConfigurationException("could not migrate " + name, exception);
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
