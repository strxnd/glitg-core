package dev.glitg.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
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
    private Map<String, YamlConfiguration> rollbackConfigurations;

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
        validateDefinitions();
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
        rollbackConfigurations = previous;
    }

    public void commitReload() { rollbackConfigurations = null; }

    public void rollbackReload() {
        if (rollbackConfigurations == null) return;
        configurations.clear();
        configurations.putAll(rollbackConfigurations);
        rollbackConfigurations = null;
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

    private void validateDefinitions() throws ConfigurationException {
        var errors = new java.util.ArrayList<String>();
        validateItems(errors);
        validateRecipes(errors);
        validateRituals(errors);
        validateKit(errors);
        if (!errors.isEmpty()) throw new ConfigurationException(String.join("; ", errors));
    }

    private void validateItems(List<String> errors) {
        YamlConfiguration items = configurations.get("items.yml");
        for (String root : List.of("rules", "limits", "protected")) {
            var section = items.getConfigurationSection(root);
            if (section == null) continue;
            for (String id : section.getKeys(false)) {
                String path = root + "." + id;
                var definition = items.getConfigurationSection(path);
                if (definition == null) { errors.add(path + " must be a section"); continue; }
                String material = definition.getString("material");
                if (material != null && Material.matchMaterial(material) == null) errors.add(path + ".material is unknown");
                if (root.equals("limits")) {
                    if (definition.getInt("maximum", 0) < 0 || (definition.contains("maximum-stacks") && definition.getInt("maximum-stacks") < 0)) {
                        errors.add(path + " limits must be non-negative");
                    }
                    try { dev.glitg.core.domain.ItemLimitScope.parse(definition.getString("scope", "CARRIED")); }
                    catch (IllegalArgumentException exception) { errors.add(path + ".scope is invalid"); }
                }
                if (root.equals("rules")) for (String action : definition.getStringList("actions")) {
                    try { dev.glitg.core.domain.ItemAction.valueOf(action.toUpperCase(java.util.Locale.ROOT)); }
                    catch (IllegalArgumentException exception) { errors.add(path + ".actions contains " + action); }
                }
            }
        }
        double chance = items.getDouble("warden-heart.drop-chance", 1.0);
        if (chance < 0 || chance > 1) errors.add("warden-heart.drop-chance must be between 0 and 1");
        if (!java.util.Set.of("RIGHT_CLICK", "DROP", "BOTH").contains(items.getString("warden-heart.acquisition", "RIGHT_CLICK").toUpperCase(java.util.Locale.ROOT))) {
            errors.add("warden-heart.acquisition must be RIGHT_CLICK, DROP, or BOTH");
        }
    }

    private void validateRecipes(List<String> errors) {
        YamlConfiguration yaml = configurations.get("recipes.yml");
        var recipes = yaml.getConfigurationSection("recipes");
        if (recipes == null) return;
        for (String id : recipes.getKeys(false)) {
            String base = "recipes." + id;
            var recipe = yaml.getConfigurationSection(base);
            if (recipe == null) { errors.add(base + " must be a section"); continue; }
            String type = recipe.getString("type", "SHAPED").toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("SHAPED", "SHAPELESS").contains(type)) errors.add(base + ".type must be SHAPED or SHAPELESS");
            if (recipe.getInt("result-amount", 1) < 1) errors.add(base + ".result-amount must be positive");
            validateItemValue(recipe.getString("result-item", recipe.getString("result-material", "AIR")), base + ".result", errors);
            var ingredients = recipe.getConfigurationSection("ingredients");
            if (ingredients == null || ingredients.getKeys(false).isEmpty()) errors.add(base + ".ingredients must not be empty");
            else for (String key : ingredients.getKeys(false)) {
                if (key.length() != 1) errors.add(base + ".ingredients keys must be one character");
                validateItemValue(ingredients.getString(key, "AIR"), base + ".ingredients." + key, errors);
            }
            if (type.equals("SHAPED")) {
                List<String> shape = recipe.getStringList("shape");
                if (shape.isEmpty() || shape.size() > 3 || shape.stream().anyMatch(row -> row.isEmpty() || row.length() > 3)) {
                    errors.add(base + ".shape must contain one to three rows of one to three characters");
                }
            }
        }
    }

    private void validateRituals(List<String> errors) {
        YamlConfiguration yaml = configurations.get("rituals.yml");
        for (String root : List.of("altars", "rituals")) {
            var section = yaml.getConfigurationSection(root);
            if (section == null) continue;
            for (String id : section.getKeys(false)) {
                String base = root + "." + id;
                var definition = yaml.getConfigurationSection(base);
                if (definition == null) { errors.add(base + " must be a section"); continue; }
                String materialPath = root.equals("altars") ? "block" : "input-material";
                if (Material.matchMaterial(definition.getString(materialPath, "AIR")) == null) errors.add(base + "." + materialPath + " is unknown");
                if (root.equals("rituals")) {
                    if (definition.getLong("duration-seconds", 0) < 0) errors.add(base + ".duration-seconds must be non-negative");
                    if (Material.matchMaterial(definition.getString("result-material", "AIR")) == null) errors.add(base + ".result-material is unknown");
                    String altar = definition.getString("altar", "");
                    if (!yaml.isConfigurationSection("altars." + altar)) errors.add(base + ".altar references an unknown altar");
                }
            }
        }
    }

    private void validateKit(List<String> errors) {
        List<String> encoded = configurations.get("kits.yml").getStringList("join-kit");
        for (int slot = 0; slot < encoded.size(); slot++) {
            if (encoded.get(slot).isBlank()) continue;
            try { dev.glitg.core.item.ItemStackCodec.decode(encoded.get(slot)); }
            catch (IOException | RuntimeException exception) { errors.add("join-kit slot " + slot + " is invalid: " + exception.getMessage()); }
        }
    }

    private static void validateItemValue(String value, String path, List<String> errors) {
        if (value == null || value.isBlank()) { errors.add(path + " is required"); return; }
        if (value.startsWith("base64:")) {
            try { dev.glitg.core.item.ItemStackCodec.decode(value.substring(7)); }
            catch (IOException | RuntimeException exception) { errors.add(path + " has invalid item data"); }
        } else {
            Material material = Material.matchMaterial(value);
            if (material == null || material.isAir()) errors.add(path + " has unknown material " + value);
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
