package dev.glitg.core.item;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.domain.ItemAction;
import dev.glitg.core.domain.ItemDescriptor;
import dev.glitg.core.domain.ItemMatcher;
import dev.glitg.core.domain.ItemLimitScope;
import dev.glitg.core.domain.ItemRule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RuleEngine {
    private final ConfigService configs;
    private final BukkitItemAdapter adapter;
    private final ItemMatcher matcher = new ItemMatcher();
    private volatile List<ItemRule> rules = List.of();
    private volatile List<Limit> limits = List.of();
    private volatile List<ProtectedDefinition> protectedItems = List.of();

    public RuleEngine(ConfigService configs, BukkitItemAdapter adapter) {
        this.configs = configs;
        this.adapter = adapter;
        reload();
    }

    public void reload() {
        rules = parseRules(configs.file("items.yml").getConfigurationSection("rules"));
        limits = parseLimits(configs.file("items.yml").getConfigurationSection("limits"));
        protectedItems = parseProtected(configs.file("items.yml").getConfigurationSection("protected"));
    }

    public ItemRule blockedRule(ItemStack stack, ItemAction action) {
        ItemDescriptor descriptor = adapter.describe(stack);
        return rules.stream().filter(rule -> rule.appliesTo(action) && matcher.matches(rule, descriptor)).findFirst().orElse(null);
    }

    public Limit matchingLimit(ItemStack stack) {
        ItemDescriptor descriptor = adapter.describe(stack);
        return limits.stream().filter(limit -> matcher.matches(limit.matcher(), descriptor)).findFirst().orElse(null);
    }

    public List<Limit> matchingLimits(ItemStack stack) {
        ItemDescriptor descriptor = adapter.describe(stack);
        return limits.stream().filter(limit -> matcher.matches(limit.matcher(), descriptor)).toList();
    }

    public List<Limit> limitGroup(Limit limit) {
        return limits.stream().filter(candidate -> candidate.group().equals(limit.group()) && candidate.scope() == limit.scope()).toList();
    }

    public ProtectedDefinition protectedDefinition(ItemStack stack) {
        ItemDescriptor descriptor = adapter.describe(stack);
        return protectedItems.stream().filter(definition -> matcher.matches(definition.matcher(), descriptor)).findFirst().orElse(null);
    }

    public synchronized String addRule(ItemStack stack, ItemAction action) throws IOException {
        ItemDescriptor item = adapter.describe(stack);
        String id = safeId(item.material()) + "-" + action.name().toLowerCase(Locale.ROOT) + "-" + System.currentTimeMillis();
        String path = "rules." + id;
        var yaml = configs.file("items.yml");
        yaml.set(path + ".enabled", true);
        yaml.set(path + ".actions", List.of(action.name()));
        writeMatcher(yaml, path, item);
        configs.save("items.yml");
        reload();
        return id;
    }

    public synchronized void setRuleEnabled(String id, boolean enabled) throws IOException {
        configs.file("items.yml").set("rules." + id + ".enabled", enabled);
        configs.save("items.yml");
        reload();
    }

    public synchronized void removeRule(String id) throws IOException {
        configs.file("items.yml").set("rules." + id, null);
        configs.save("items.yml");
        reload();
    }

    public synchronized String setLimit(ItemStack stack, int maximum) throws IOException {
        ItemDescriptor item = adapter.describe(stack);
        int fingerprint = java.util.Objects.hash(item.material(), item.potion(), item.customModelData(),
                item.persistentData(), item.enchantments(), item.tags());
        String id = safeId(item.material()) + "-" + Integer.toUnsignedString(fingerprint, 36);
        String path = "limits." + id;
        var yaml = configs.file("items.yml");
        yaml.set(path + ".enabled", true);
        yaml.set(path + ".maximum", maximum);
        yaml.set(path + ".maximum-stacks", null);
        yaml.set(path + ".scope", ItemLimitScope.parse(configs.main().getString("items.limit-scope", "CARRIED")).name());
        yaml.set(path + ".group", id);
        writeMatcher(yaml, path, item);
        configs.save("items.yml");
        reload();
        return id;
    }

    public synchronized boolean removeLimit(ItemStack stack) throws IOException {
        Limit limit = matchingLimit(stack);
        if (limit == null) return false;
        configs.file("items.yml").set("limits." + limit.id(), null);
        configs.save("items.yml");
        reload();
        return true;
    }

    public synchronized void setLimitMaximum(String id, int maximum) throws IOException {
        if (maximum < 0) throw new IllegalArgumentException("limit must be non-negative");
        configs.file("items.yml").set("limits." + id + ".maximum", maximum);
        configs.file("items.yml").set("limits." + id + ".maximum-stacks", null);
        configs.save("items.yml");
        reload();
    }

    public synchronized void setLimitMaximumStacks(String id, int stacks) throws IOException {
        if (stacks < 0) throw new IllegalArgumentException("stack limit must be non-negative");
        configs.file("items.yml").set("limits." + id + ".maximum-stacks", stacks);
        configs.file("items.yml").set("limits." + id + ".maximum", 0);
        configs.save("items.yml");
        reload();
    }

    public synchronized void setLimitScope(String id, ItemLimitScope scope) throws IOException {
        configs.file("items.yml").set("limits." + id + ".scope", scope.name());
        configs.save("items.yml");
        reload();
    }

    public synchronized void setLimitGroup(String id, String group) throws IOException {
        if (group == null || !group.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("group must be a simple ID");
        configs.file("items.yml").set("limits." + id + ".group", group);
        configs.save("items.yml");
        reload();
    }

    public synchronized void removeLimit(String id) throws IOException {
        configs.file("items.yml").set("limits." + id, null);
        configs.save("items.yml");
        reload();
    }

    public synchronized String addProtected(ItemStack stack) throws IOException {
        ItemDescriptor item = adapter.describe(stack);
        String id = safeId(item.material()) + "-" + System.currentTimeMillis();
        String path = "protected." + id;
        var yaml = configs.file("items.yml");
        yaml.set(path + ".enabled", true);
        writeMatcher(yaml, path, item);
        yaml.set(path + ".immortal", true);
        yaml.set(path + ".glowing", false);
        yaml.set(path + ".stop-storage", false);
        configs.save("items.yml");
        reload();
        return id;
    }

    public synchronized void setProtectedFlag(String id, String flag, boolean value) throws IOException {
        if (!Set.of("enabled", "immortal", "glowing", "stop-storage").contains(flag)) {
            throw new IllegalArgumentException("unknown protected-item flag " + flag);
        }
        configs.file("items.yml").set("protected." + id + "." + flag, value);
        configs.save("items.yml");
        reload();
    }

    public synchronized void removeProtected(String id) throws IOException {
        configs.file("items.yml").set("protected." + id, null);
        configs.save("items.yml");
        reload();
    }

    private static List<ItemRule> parseRules(ConfigurationSection root) {
        if (root == null) return List.of();
        var parsed = new ArrayList<ItemRule>();
        for (String id : root.getKeys(false)) parsed.add(parseMatcher(id, root.getConfigurationSection(id), parseActions(root.getStringList(id + ".actions"))));
        return List.copyOf(parsed);
    }

    private static List<Limit> parseLimits(ConfigurationSection root) {
        if (root == null) return List.of();
        var parsed = new ArrayList<Limit>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) parsed.add(new Limit(id, section.getInt("maximum", 0),
                    section.contains("maximum-stacks") ? section.getInt("maximum-stacks") : null,
                    section.getString("group", id), ItemLimitScope.parse(section.getString("scope", "CARRIED")),
                    parseMatcher(id, section, Set.of(ItemAction.ALL))));
        }
        return List.copyOf(parsed);
    }

    private static List<ProtectedDefinition> parseProtected(ConfigurationSection root) {
        if (root == null) return List.of();
        var parsed = new ArrayList<ProtectedDefinition>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) parsed.add(new ProtectedDefinition(id,
                    section.getBoolean("immortal", false), section.getBoolean("glowing", false),
                    section.getBoolean("stop-storage", false), parseMatcher(id, section, Set.of(ItemAction.ALL))));
        }
        return List.copyOf(parsed);
    }

    private static ItemRule parseMatcher(String id, ConfigurationSection section, Set<ItemAction> actions) {
        if (section == null) throw new IllegalArgumentException("item definition " + id + " must be a section");
        Map<String, String> pdc = new HashMap<>();
        ConfigurationSection pdcSection = section.getConfigurationSection("persistent-data");
        if (pdcSection != null) pdcSection.getKeys(false).forEach(key -> pdc.put(key, pdcSection.getString(key, "")));
        Map<String, Integer> enchants = new HashMap<>();
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection != null) enchantSection.getKeys(false).forEach(key -> enchants.put(key, enchantSection.getInt(key)));
        return new ItemRule(id, section.getBoolean("enabled", true), actions,
                section.getString("material"), section.getString("potion"),
                section.contains("custom-model-data") ? section.getInt("custom-model-data") : null,
                pdc, enchants, new HashSet<>(section.getStringList("tags")));
    }

    private static Set<ItemAction> parseActions(List<String> raw) {
        if (raw.isEmpty()) return Set.of(ItemAction.ALL);
        EnumSet<ItemAction> result = EnumSet.noneOf(ItemAction.class);
        raw.forEach(value -> result.add(ItemAction.valueOf(value.toUpperCase(Locale.ROOT))));
        return Set.copyOf(result);
    }

    private static String safeId(String value) { return value.replace(':', '-').replaceAll("[^a-zA-Z0-9_-]", "-"); }

    private static void writeMatcher(org.bukkit.configuration.file.YamlConfiguration yaml, String path, ItemDescriptor item) {
        yaml.set(path + ".material", item.material());
        yaml.set(path + ".potion", item.potion());
        yaml.set(path + ".custom-model-data", item.customModelData());
        yaml.set(path + ".persistent-data", item.persistentData().isEmpty() ? null : item.persistentData());
        yaml.set(path + ".enchantments", item.enchantments().isEmpty() ? null : item.enchantments());
        yaml.set(path + ".tags", item.tags().isEmpty() ? null : item.tags().stream().sorted().toList());
    }

    public List<ItemRule> rules() { return rules; }
    public List<Limit> limits() { return limits; }
    public List<ProtectedDefinition> protectedItems() { return protectedItems; }

    public record Limit(String id, int maximum, Integer maximumStacks, String group,
                        ItemLimitScope scope, ItemRule matcher) {
        public Limit {
            if (maximum < 0 || (maximumStacks != null && maximumStacks < 0)) {
                throw new IllegalArgumentException("item limit cannot be negative");
            }
            group = group == null || group.isBlank() ? id : group;
        }

        public int maximumFor(ItemStack item) {
            if (maximumStacks == null) return maximum;
            long value = (long) maximumStacks * Math.max(1, item.getMaxStackSize());
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        }
    }
    public record ProtectedDefinition(String id, boolean immortal, boolean glowing, boolean stopStorage, ItemRule matcher) {}
}
