package dev.glitg.core.crafting;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.item.ItemStackCodec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CraftingService {
    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final Map<NamespacedKey, RecipeDefinition> registered = new HashMap<>();

    public CraftingService(JavaPlugin plugin, ConfigService configs) {
        this.plugin = plugin; this.configs = configs;
    }

    public synchronized void reload() {
        Map<NamespacedKey, RecipeDefinition> candidate = new java.util.LinkedHashMap<>();
        if (configs.enabled("custom-crafting")) {
            ConfigurationSection root = configs.file("recipes.yml").getConfigurationSection("recipes");
            if (root != null) for (String id : root.getKeys(false)) {
                try {
                    RecipeDefinition definition = parse(id, root.getConfigurationSection(id));
                    if (!definition.enabled()) continue;
                    NamespacedKey key = new NamespacedKey(plugin, safe(id));
                    build(key, definition); // validate all definitions before touching live recipes
                    if (candidate.putIfAbsent(key, definition) != null) {
                        throw new IllegalArgumentException("recipe IDs collide after normalization: " + id);
                    }
                } catch (RuntimeException | IOException exception) {
                    throw new IllegalArgumentException("Recipe " + id + " is invalid: " + exception.getMessage(), exception);
                }
            }
        }

        Map<NamespacedKey, RecipeDefinition> previous = Map.copyOf(registered);
        registered.keySet().forEach(Bukkit::removeRecipe);
        registered.clear();
        try {
            for (var entry : candidate.entrySet()) {
                if (!Bukkit.addRecipe(build(entry.getKey(), entry.getValue()))) {
                    throw new IllegalArgumentException("recipe key or shape conflicts with an existing recipe: " + entry.getKey());
                }
                registered.put(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException | IOException exception) {
            registered.keySet().forEach(Bukkit::removeRecipe);
            registered.clear();
            try {
                for (var entry : previous.entrySet()) {
                    Bukkit.addRecipe(build(entry.getKey(), entry.getValue()));
                    registered.put(entry.getKey(), entry.getValue());
                }
            } catch (RuntimeException | IOException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw new IllegalArgumentException("Recipe reload rolled back: " + exception.getMessage(), exception);
        }
    }

    private RecipeDefinition parse(String id, ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("definition must be a section");
        RecipeDefinition.Type type = RecipeDefinition.Type.valueOf(section.getString("type", "SHAPED").toUpperCase(Locale.ROOT));
        String result = section.getString("result-item", section.getString("result-material", "AIR"));
        Map<Character, String> ingredients = new HashMap<>();
        ConfigurationSection choices = section.getConfigurationSection("ingredients");
        if (choices != null) choices.getKeys(false).forEach(key -> {
            if (key.length() != 1) throw new IllegalArgumentException("ingredient keys must be one character");
            ingredients.put(key.charAt(0), choices.getString(key, "AIR"));
        });
        return new RecipeDefinition(id, section.getBoolean("enabled", true), type, result,
                section.getInt("result-amount", 1), section.getStringList("shape"), ingredients);
    }

    private Recipe build(NamespacedKey key, RecipeDefinition definition) throws IOException {
        ItemStack result = item(definition.result(), definition.resultAmount());
        if (definition.type() == RecipeDefinition.Type.SHAPED) {
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(definition.shape().toArray(String[]::new));
            for (var entry : definition.ingredients().entrySet()) recipe.setIngredient(entry.getKey(), choice(entry.getValue()));
            return recipe;
        }
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (String value : definition.ingredients().values()) recipe.addIngredient(choice(value));
        return recipe;
    }

    private static RecipeChoice choice(String encodedOrMaterial) throws IOException {
        if (encodedOrMaterial.startsWith("base64:")) return new RecipeChoice.ExactChoice(ItemStackCodec.decode(encodedOrMaterial.substring(7)));
        Material material = Material.matchMaterial(encodedOrMaterial);
        if (material == null || material.isAir()) throw new IllegalArgumentException("unknown ingredient material " + encodedOrMaterial);
        return new RecipeChoice.MaterialChoice(material);
    }

    private static ItemStack item(String encodedOrMaterial, int amount) throws IOException {
        ItemStack item;
        if (encodedOrMaterial.startsWith("base64:")) item = ItemStackCodec.decode(encodedOrMaterial.substring(7));
        else {
            Material material = Material.matchMaterial(encodedOrMaterial);
            if (material == null || material.isAir()) throw new IllegalArgumentException("unknown result material " + encodedOrMaterial);
            item = new ItemStack(material);
        }
        item.setAmount(Math.min(item.getMaxStackSize(), amount));
        return item;
    }

    public RecipeDefinition definition(NamespacedKey key) { return registered.get(key); }
    public List<RecipeDefinition> definitions() { return List.copyOf(registered.values()); }

    public List<RecipeDefinition> configuredDefinitions() {
        ConfigurationSection root = configs.file("recipes.yml").getConfigurationSection("recipes");
        if (root == null) return List.of();
        var definitions = new ArrayList<RecipeDefinition>();
        for (String id : root.getKeys(false)) definitions.add(parse(id, root.getConfigurationSection(id)));
        return List.copyOf(definitions);
    }

    public RecipeDefinition configuredDefinition(String id) {
        ConfigurationSection section = configs.file("recipes.yml").getConfigurationSection("recipes." + id);
        return section == null ? null : parse(id, section);
    }

    public void saveShaped(String id, ItemStack result, ItemStack[] matrix) throws IOException {
        saveRecipe(id, RecipeDefinition.Type.SHAPED, result, matrix);
    }

    public void saveRecipe(String id, RecipeDefinition.Type type, ItemStack result, ItemStack[] matrix) throws IOException {
        if (matrix.length != 9) throw new IllegalArgumentException("matrix must contain 9 slots");
        var yaml = configs.file("recipes.yml");
        String base = "recipes." + id;
        yaml.set(base + ".enabled", true); yaml.set(base + ".type", type.name());
        yaml.set(base + ".result-item", "base64:" + ItemStackCodec.encode(result)); yaml.set(base + ".result-amount", result.getAmount());
        List<String> shape = new ArrayList<>(); Map<String, String> ingredients = new HashMap<>();
        Map<String, Character> signatures = new HashMap<>(); char next = 'A';
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < 3; column++) {
                ItemStack item = matrix[row * 3 + column];
                if (item == null || item.getType().isAir()) { line.append(' '); continue; }
                String encoded = "base64:" + ItemStackCodec.encode(item.asOne());
                Character symbol = type == RecipeDefinition.Type.SHAPED ? signatures.get(encoded) : null;
                if (symbol == null) { symbol = next++; signatures.put(encoded, symbol); ingredients.put(String.valueOf(symbol), encoded); }
                line.append(symbol);
            }
            shape.add(line.toString());
        }
        yaml.set(base + ".shape", type == RecipeDefinition.Type.SHAPED ? shape : null);
        yaml.set(base + ".ingredients", ingredients);
        configs.save("recipes.yml"); reload();
    }

    public void setEnabled(String id, boolean enabled) throws IOException {
        configs.file("recipes.yml").set("recipes." + id + ".enabled", enabled);
        configs.save("recipes.yml");
        reload();
    }

    public void remove(String id) throws IOException {
        configs.file("recipes.yml").set("recipes." + id, null);
        configs.save("recipes.yml");
        reload();
    }

    private static String safe(String id) { return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"); }
}
