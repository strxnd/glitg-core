package dev.glitg.core.gui;

import dev.glitg.core.GLITGCorePlugin;
import dev.glitg.core.config.ConfigService;
import dev.glitg.core.config.DurationParser;
import dev.glitg.core.crafting.CraftingService;
import dev.glitg.core.crafting.RecipeDefinition;
import dev.glitg.core.domain.ItemAction;
import dev.glitg.core.domain.ItemRule;
import dev.glitg.core.item.EnchantPolicyService;
import dev.glitg.core.item.ItemStackCodec;
import dev.glitg.core.item.PotionPolicyService;
import dev.glitg.core.item.RuleEngine;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.service.KitService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Inventory-native administration console. All top-inventory items are virtual controls: the GUI never
 * consumes or manufactures player items. Configuration writes are persisted immediately and the affected
 * runtime services are refreshed before the menu reports success.
 */
public final class AdminGuiService implements Listener {
    private static final int[] CONTENT_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private static final int[] RECIPE_SLOTS = {10,11,12,19,20,21,28,29,30};
    private static final int PAGE_SIZE = CONTENT_SLOTS.length;

    private final GLITGCorePlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final CraftingService crafting;
    private final RuleEngine rules;
    private final EnchantPolicyService enchants;
    private final PotionPolicyService potions;
    private final KitService kits;
    private final Map<UUID, InputPrompt> prompts = new ConcurrentHashMap<>();
    private final Map<String, DynamicSchema> dynamicSchemas = buildDynamicSchemas();

    public AdminGuiService(GLITGCorePlugin plugin, ConfigService configs, MessageService messages,
                           CraftingService crafting, RuleEngine rules, EnchantPolicyService enchants,
                           PotionPolicyService potions, KitService kits) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.crafting = crafting;
        this.rules = rules;
        this.enchants = enchants;
        this.potions = potions;
        this.kits = kits;
    }

    public void open(Player player) {
        openCategory(player, GuiCatalog.Category.RULES);
    }

    /** Validates the complete static control catalog against loaded configuration and live Paper registries. */
    public int validate() {
        int controls = 0;
        for (GuiCatalog.Category category : GuiCatalog.Category.values()) {
            List<GuiCatalog.Feature> features = GuiCatalog.features(category);
            if (features.size() > CONTENT_SLOTS.length) throw new IllegalStateException(category.label + " exceeds one settings page");
            for (GuiCatalog.Feature feature : features) {
                if (!configs.main().contains("features." + feature.key())) throw new IllegalStateException("missing feature key " + feature.key());
                controls++;
                for (GuiCatalog.Setting setting : feature.settings()) {
                    if (configs.file(setting.file()) == null || !configs.file(setting.file()).contains(setting.path())) {
                        throw new IllegalStateException("missing GUI setting " + setting.file() + ":" + setting.path());
                    }
                    controls++;
                }
            }
        }
        if (PotionType.values().length == 0) throw new IllegalStateException("Paper potion registry is empty");
        if (!RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).iterator().hasNext()) {
            throw new IllegalStateException("Paper enchantment registry is empty");
        }
        for (DynamicSchema schema : dynamicSchemas.values()) controls += schema.settings.size();
        return controls;
    }

    public void openItemRuleEditor(Player player, ItemStack held) {
        if (held.getItemMeta() instanceof PotionMeta potionMeta && potionMeta.getBasePotionType() != null) {
            List<PotionType> types = Arrays.stream(PotionType.values()).sorted(Comparator.comparing(type -> type.getKey().asString())).toList();
            openPotions(player, Math.max(0, types.indexOf(potionMeta.getBasePotionType())) / PAGE_SIZE);
        } else if (!held.getEnchantments().isEmpty() || held.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
            Set<Enchantment> itemEnchants = new java.util.HashSet<>(held.getEnchantments().keySet());
            if (held.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storage) itemEnchants.addAll(storage.getStoredEnchants().keySet());
            List<Enchantment> values = new ArrayList<>();
            RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).forEach(values::add);
            values.sort(Comparator.comparing(enchantment -> enchantment.getKey().asString()));
            int position = itemEnchants.stream().mapToInt(values::indexOf).filter(index -> index >= 0).min().orElse(0);
            openEnchantments(player, position / PAGE_SIZE);
        } else {
            openRuleActions(player, held.clone());
        }
    }

    public void openPotionPolicy(Player player) { openPotions(player, 0); }
    public void openEnchantmentPolicy(Player player) { openEnchantments(player, 0); }

    public void openRecipeEditor(Player player, String id) {
        RecipeDefinition definition = crafting.configuredDefinition(id);
        RecipeDefinition.Type type = definition == null ? RecipeDefinition.Type.SHAPED : definition.type();
        MenuHolder holder = new MenuHolder(MenuType.RECIPE_EDITOR, GuiCatalog.Category.OTHER, "custom-crafting", 0, id);
        holder.recipeType = type;
        Inventory inventory = create(holder, 54, menuTitle("Recipe", id));
        holder.inventory = inventory;
        fillFrame(inventory);
        if (definition != null) loadRecipe(inventory, definition);
        renderRecipeControls(holder);
        player.openInventory(inventory);
    }

    private void openCategory(Player player, GuiCatalog.Category category) {
        MenuHolder holder = new MenuHolder(MenuType.CATEGORY, category, null, 0, null);
        Inventory inventory = create(holder, 54, menuTitle(category.label));
        holder.inventory = inventory;
        fillFrame(inventory);
        renderTabs(inventory, category);
        List<GuiCatalog.Feature> features = GuiCatalog.features(category);
        for (int index = 0; index < features.size() && index < PAGE_SIZE; index++) {
            GuiCatalog.Feature feature = features.get(index);
            boolean enabled = configs.enabled(feature.key());
            List<Component> lore = new ArrayList<>();
            feature.description().forEach(line -> lore.add(text("<gray>" + line + "</gray>")));
            lore.add(Component.empty());
            lore.add(status(enabled));
            lore.add(text("<gold>Left-click</gold> <white>Configure</white>"));
            lore.add(text("<yellow>Right-click</yellow> <white>" + (enabled ? "Disable" : "Enable") + "</white>"));
            inventory.setItem(CONTENT_SLOTS[index], icon(feature.icon(), text("<white>" + feature.label() + "</white>"), lore, enabled));
        }
        inventory.setItem(48, icon(Material.BARRIER, text("<red>Close</red>"), List.of(), false));
        inventory.setItem(50, icon(Material.CLOCK, text("<gold>Reload</gold>"),
                List.of(text("<gray>Reload and validate all settings.</gray>"),
                        text("<gold>Click</gold> <white>Reload now</white>")), true));
        player.openInventory(inventory);
    }

    private void renderTabs(Inventory inventory, GuiCatalog.Category selected) {
        int[] slots = {1, 4, 7};
        GuiCatalog.Category[] categories = GuiCatalog.Category.values();
        for (int index = 0; index < categories.length; index++) {
            GuiCatalog.Category category = categories[index];
            inventory.setItem(slots[index], icon(category.icon,
                    text((category == selected ? "<gold><bold>◆ " : "<gray>") + category.label + (category == selected ? "</bold>" : "")),
                    List.of(text(category == selected
                            ? "<gold>Selected</gold>"
                            : "<gray>Click to open.</gray>")), category == selected));
        }
    }

    private void openFeature(Player player, GuiCatalog.Feature feature) {
        MenuHolder holder = new MenuHolder(MenuType.FEATURE, feature.category(), feature.key(), 0, null);
        Inventory inventory = create(holder, 54, menuTitle(feature.label()));
        holder.inventory = inventory;
        fillFrame(inventory);
        boolean enabled = configs.enabled(feature.key());
        List<Component> masterLore = new ArrayList<>();
        feature.description().forEach(line -> masterLore.add(text("<gray>" + line + "</gray>")));
        masterLore.add(Component.empty());
        masterLore.add(status(enabled));
        masterLore.add(text("<gold>Click</gold> <white>" + (enabled ? "Disable" : "Enable") + "</white>"));
        inventory.setItem(4, icon(feature.icon(), text("<gold><bold>◆ " + feature.label() + "</bold></gold>"), masterLore, enabled));

        int offset = 0;
        if (feature.special() != GuiCatalog.Special.NONE) {
            inventory.setItem(CONTENT_SLOTS[offset++], icon(specialIcon(feature.special()), text("<gold>Manage " + feature.label().toLowerCase(Locale.ROOT) + "</gold>"),
                    List.of(text("<gray>Open the editor.</gray>"),
                            text("<gold>Click</gold> <white>Open</white>")), true));
        }
        for (GuiCatalog.Setting setting : feature.settings()) {
            if (offset >= CONTENT_SLOTS.length) break;
            inventory.setItem(CONTENT_SLOTS[offset++], settingIcon(setting));
        }
        if (feature.settings().isEmpty() && feature.special() == GuiCatalog.Special.NONE) {
            inventory.setItem(22, icon(Material.WRITABLE_BOOK, text("<yellow>Master control only</yellow>"),
                    List.of(text("<gray>This feature is controlled by its master switch.</gray>")), false));
        }
        inventory.setItem(45, backIcon("Back to " + feature.category().label));
        inventory.setItem(49, icon(Material.COMPASS, text("<gold>All " + feature.category().label.toLowerCase(Locale.ROOT) + " settings</gold>"),
                List.of(text("<gray>Return to the category.</gray>")), true));
        player.openInventory(inventory);
    }

    private ItemStack settingIcon(GuiCatalog.Setting setting) {
        Object value = configs.file(setting.file()).get(setting.path());
        boolean active = value instanceof Boolean flag && flag;
        List<Component> lore = new ArrayList<>();
        setting.description().forEach(line -> lore.add(text("<gray>" + line + "</gray>")));
        lore.add(Component.empty());
        lore.add(text("<dark_gray>Current:</dark_gray> <white>" + displayValue(value) + "</white>"));
        if (setting.type() == GuiCatalog.ValueType.BOOLEAN) {
            lore.add(text("<gold>Click</gold> <white>Toggle</white>"));
        } else if (setting.type() == GuiCatalog.ValueType.ENUM) {
            lore.add(text("<gold>Left-click</gold> <white>Next</white>"));
            lore.add(text("<yellow>Right-click</yellow> <white>Previous</white>"));
        } else {
            lore.add(text("<gold>Click</gold> <white>Edit in chat</white>"));
        }
        return icon(setting.icon(), text("<white>" + setting.label() + "</white>"), lore, active);
    }

    private void openItemRules(Player player, int page) {
        MenuHolder holder = new MenuHolder(MenuType.ITEM_RULES, GuiCatalog.Category.RULES, "item-rules", page, null);
        Inventory inventory = paged(holder, "Item restrictions");
        List<ItemRule> entries = rules.rules();
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < entries.size(); index++) {
            ItemRule rule = entries.get(start + index);
            List<Component> lore = new ArrayList<>();
            lore.add(text("<dark_gray>ID:</dark_gray> <gray>" + rule.id() + "</gray>"));
            lore.add(text("<dark_gray>Actions:</dark_gray> <white>" + joinActions(rule) + "</white>"));
            if (rule.potion() != null) lore.add(text("<dark_gray>Potion:</dark_gray> <white>" + rule.potion() + "</white>"));
            lore.add(Component.empty());
            lore.add(status(rule.enabled()));
            lore.add(text("<gold>› Left-click</gold> <white>Toggle</white>"));
            lore.add(text("<red>› Right-click</red> <white>Remove</white>"));
            inventory.setItem(CONTENT_SLOTS[index], icon(material(rule.material()), text("<white>" + friendly(rule.material()) + "</white>"), lore, rule.enabled()));
        }
        finishPaged(inventory, page, entries.size(), "Add held item");
        player.openInventory(inventory);
    }

    private void openRuleActions(Player player, ItemStack held) {
        MenuHolder holder = new MenuHolder(MenuType.RULE_ACTIONS, GuiCatalog.Category.RULES, "item-rules", 0, held.clone());
        Inventory inventory = create(holder, 27, menuTitle("Restriction mode"));
        holder.inventory = inventory;
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        ItemStack preview = held.clone();
        ItemMeta previewMeta = preview.getItemMeta();
        previewMeta.lore(nonItalic(List.of(text("<gray>Choose how this exact item is restricted.</gray>"))));
        preview.setItemMeta(previewMeta);
        inventory.setItem(4, preview);
        ItemAction[] actions = ItemAction.values();
        int[] slots = {9,10,11,12,13,14,15,16,17};
        for (int index = 0; index < actions.length; index++) {
            ItemAction action = actions[index];
            inventory.setItem(slots[index], icon(actionIcon(action), text("<white>" + friendly(action.name()) + "</white>"),
                    List.of(text("<gray>" + actionDescription(action) + "</gray>"),
                            text("<gold>› Click</gold> <white>Create rule</white>")), action == ItemAction.ALL));
        }
        inventory.setItem(22, backIcon("Cancel"));
        player.openInventory(inventory);
    }

    private void openItemLimits(Player player, int page) {
        MenuHolder holder = new MenuHolder(MenuType.ITEM_LIMITS, GuiCatalog.Category.RULES, "item-limits", page, null);
        Inventory inventory = paged(holder, "Item limits");
        List<RuleEngine.Limit> entries = rules.limits();
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < entries.size(); index++) {
            RuleEngine.Limit limit = entries.get(start + index);
            ItemRule matcher = limit.matcher();
            List<Component> lore = List.of(
                    text("<dark_gray>ID:</dark_gray> <gray>" + limit.id() + "</gray>"),
                    text("<dark_gray>Maximum:</dark_gray> <white>" + limit.maximum() + "</white>"),
                    Component.empty(),
                    text("<gold>› Left-click</gold> <white>Change maximum</white>"),
                    text("<red>› Right-click</red> <white>Remove</white>"));
            inventory.setItem(CONTENT_SLOTS[index], icon(material(matcher.material()), text("<white>" + friendly(matcher.material()) + "</white>"), lore, true));
        }
        finishPaged(inventory, page, entries.size(), "Limit held item");
        player.openInventory(inventory);
    }

    private void openProtectedItems(Player player, int page) {
        MenuHolder holder = new MenuHolder(MenuType.PROTECTED_ITEMS, GuiCatalog.Category.RULES, "protected-items", page, null);
        Inventory inventory = paged(holder, "Special items");
        List<RuleEngine.ProtectedDefinition> entries = rules.protectedItems();
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < entries.size(); index++) {
            RuleEngine.ProtectedDefinition entry = entries.get(start + index);
            List<Component> lore = List.of(
                    text("<dark_gray>ID:</dark_gray> <gray>" + entry.id() + "</gray>"),
                    flagLine("Death protected", entry.immortal()), flagLine("Glowing", entry.glowing()),
                    flagLine("Cannot store", entry.stopStorage()), Component.empty(),
                    text("<gold>› Left-click</gold> <white>Configure</white>"),
                    text("<red>› Right-click</red> <white>Remove</white>"));
            inventory.setItem(CONTENT_SLOTS[index], icon(material(entry.matcher().material()), text("<white>" + friendly(entry.matcher().material()) + "</white>"), lore, entry.matcher().enabled()));
        }
        finishPaged(inventory, page, entries.size(), "Add held item");
        player.openInventory(inventory);
    }

    private void openProtectedEntry(Player player, String id) {
        RuleEngine.ProtectedDefinition entry = rules.protectedItems().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
        if (entry == null) { openProtectedItems(player, 0); return; }
        MenuHolder holder = new MenuHolder(MenuType.PROTECTED_ENTRY, GuiCatalog.Category.RULES, "protected-items", 0, id);
        Inventory inventory = create(holder, 45, menuTitle("Special item", friendly(entry.matcher().material())));
        holder.inventory = inventory;
        fillFrame(inventory);
        inventory.setItem(4, icon(material(entry.matcher().material()), text("<white>" + friendly(entry.matcher().material()) + "</white>"),
                List.of(text("<dark_gray>Definition:</dark_gray> <gray>" + id + "</gray>")), true));
        inventory.setItem(20, toggleCard(Material.TOTEM_OF_UNDYING, "Death protected", entry.immortal(), "Keep this item through player death."));
        inventory.setItem(22, toggleCard(Material.GLOW_INK_SAC, "Glowing", entry.glowing(), "Apply an explicit enchantment glint."));
        inventory.setItem(24, toggleCard(Material.ENDER_CHEST, "Cannot store", entry.stopStorage(), "Block storage in external inventories."));
        inventory.setItem(36, backIcon("Back to special items"));
        inventory.setItem(44, icon(Material.TNT, text("<red>Remove definition</red>"), List.of(text("<gray>Requires confirmation.</gray>")), false));
        player.openInventory(inventory);
    }

    private void openPotions(Player player, int page) {
        MenuHolder holder = new MenuHolder(MenuType.POTIONS, GuiCatalog.Category.RULES, "potion-policy", page, null);
        Inventory inventory = paged(holder, "Potion policy");
        List<PotionType> types = Arrays.stream(PotionType.values()).sorted(Comparator.comparing(type -> type.getKey().asString())).toList();
        Set<String> banned = normalizedSet(configs.file("items.yml").getStringList("potion-policy.banned"));
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < types.size(); index++) {
            PotionType type = types.get(start + index);
            String key = type.getKey().asString().toLowerCase(Locale.ROOT);
            boolean blocked = banned.contains(key);
            ItemStack potion = new ItemStack(Material.POTION);
            if (potion.getItemMeta() instanceof PotionMeta meta) {
                try { meta.setBasePotionType(type); } catch (IllegalArgumentException ignored) { /* uncraftable registry entry */ }
                meta.itemName(nonItalic(text((blocked ? "<red>" : "<white>") + friendly(type.name()) + "</" + (blocked ? "red" : "white") + ">")));
                meta.lore(nonItalic(List.of(text("<dark_gray>" + key + "</dark_gray>"), status(blocked, "Banned", "Allowed"),
                        text("<gold>› Click</gold> <white>" + (blocked ? "Allow" : "Ban") + " potion</white>"))));
                potion.setItemMeta(meta);
            }
            inventory.setItem(CONTENT_SLOTS[index], potion);
        }
        finishPaged(inventory, page, types.size(), null);
        inventory.setItem(46, toggleCard(Material.POTION, "Ban all Tier I", configs.file("items.yml").getBoolean("potion-policy.ban-tier-1"), "Apply to every Tier I potion."));
        inventory.setItem(52, toggleCard(Material.SPLASH_POTION, "Ban all Tier II", configs.file("items.yml").getBoolean("potion-policy.ban-tier-2"), "Apply to every Tier II potion."));
        player.openInventory(inventory);
    }

    private void openEnchantments(Player player, int page) {
        MenuHolder holder = new MenuHolder(MenuType.ENCHANTMENTS, GuiCatalog.Category.RULES, "enchant-policy", page, null);
        Inventory inventory = paged(holder, "Enchant policy");
        List<Enchantment> values = new ArrayList<>();
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).forEach(values::add);
        values.sort(Comparator.comparing(enchantment -> enchantment.getKey().asString()));
        Set<String> banned = normalizedSet(configs.file("enchants.yml").getStringList("banned"));
        ConfigurationSection maximums = configs.file("enchants.yml").getConfigurationSection("maximum-levels");
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < values.size(); index++) {
            Enchantment enchantment = values.get(start + index);
            String key = enchantment.getKey().asString().toLowerCase(Locale.ROOT);
            boolean blocked = banned.contains(key);
            Integer maximum = maximums != null && maximums.contains(key) ? maximums.getInt(key) : null;
            List<Component> lore = new ArrayList<>();
            lore.add(text("<dark_gray>" + key + "</dark_gray>"));
            lore.add(status(blocked, "Banned", "Allowed"));
            lore.add(text("<dark_gray>Maximum:</dark_gray> <white>" + (maximum == null ? "vanilla" : maximum) + "</white>"));
            lore.add(Component.empty());
            lore.add(text("<gold>› Left-click</gold> <white>Ban / allow</white>"));
            lore.add(text("<yellow>› Right-click</yellow> <white>Set maximum</white>"));
            lore.add(text("<red>› Shift-right-click</red> <white>Clear maximum</white>"));
            inventory.setItem(CONTENT_SLOTS[index], icon(Material.ENCHANTED_BOOK, text((blocked ? "<red>" : "<white>") + friendly(key) + "</" + (blocked ? "red" : "white") + ">"), lore, !blocked));
        }
        finishPaged(inventory, page, values.size(), null);
        player.openInventory(inventory);
    }

    private void openRecipes(Player player, int page) {
        MenuHolder holder = new MenuHolder(MenuType.RECIPES, GuiCatalog.Category.OTHER, "custom-crafting", page, null);
        Inventory inventory = paged(holder, "Custom recipes");
        List<RecipeDefinition> entries;
        try { entries = crafting.configuredDefinitions().stream().sorted(Comparator.comparing(RecipeDefinition::id)).toList(); }
        catch (RuntimeException exception) {
            entries = List.of();
            inventory.setItem(22, errorIcon("A recipe definition is invalid", exception.getMessage()));
        }
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < entries.size(); index++) {
            RecipeDefinition entry = entries.get(start + index);
            ItemStack display = decode(entry.result(), entry.resultAmount(), Material.CRAFTING_TABLE);
            ItemMeta meta = display.getItemMeta();
            meta.itemName(nonItalic(text("<white>" + entry.id() + "</white>")));
            meta.lore(nonItalic(List.of(status(entry.enabled()), text("<dark_gray>Type:</dark_gray> <white>" + friendly(entry.type().name()) + "</white>"),
                    Component.empty(), text("<gold>› Left-click</gold> <white>Edit</white>"),
                    text("<yellow>› Right-click</yellow> <white>Enable / disable</white>"),
                    text("<red>› Shift-right-click</red> <white>Remove</white>"))));
            display.setItemMeta(meta);
            inventory.setItem(CONTENT_SLOTS[index], display);
        }
        finishPaged(inventory, page, entries.size(), "Create recipe");
        player.openInventory(inventory);
    }

    private void openKit(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.KITS, GuiCatalog.Category.OTHER, "join-kit", 0, null);
        Inventory inventory = create(holder, 54, menuTitle("Join kit"));
        holder.inventory = inventory;
        fillFrame(inventory);
        List<String> encoded = configs.file("kits.yml").getStringList("join-kit");
        for (int index = 0; index < Math.min(36, encoded.size()); index++) {
            String value = encoded.get(index);
            if (!value.isBlank()) inventory.setItem(index + 9, decode("base64:" + value, 1, Material.BARRIER));
        }
        inventory.setItem(1, toggleCard(Material.LEVER, "Give on first join", kits.joinEnabled(), "Enable or disable automatic join-kit delivery."));
        inventory.setItem(3, icon(Material.WRITABLE_BOOK, text("<gold>Save your inventory</gold>"),
                List.of(text("<gray>Replace the configured kit with your exact inventory.</gray>"),
                        text("<yellow>Confirmation required</yellow>")), true));
        inventory.setItem(5, icon(Material.CHEST_MINECART, text("<gold>Give kit to yourself</gold>"),
                List.of(text("<gray>Add a test copy without clearing your inventory.</gray>")), true));
        inventory.setItem(7, icon(Material.LAVA_BUCKET, text("<red>Clear configured kit</red>"),
                List.of(text("<gray>Requires confirmation.</gray>")), false));
        inventory.setItem(49, backIcon("Back to Content"));
        player.openInventory(inventory);
    }

    private void openAdminUtilities(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.ADMIN_UTILITIES, GuiCatalog.Category.OTHER, "admin-utilities", 0, null);
        Inventory inventory = create(holder, 45, menuTitle("Admin utilities"));
        holder.inventory = inventory;
        fillFrame(inventory);
        List<CommandCard> cards = List.of(
                new CommandCard(Material.CHEST, "/invsee <player>", "Edit an online player's inventory."),
                new CommandCard(Material.ENDER_CHEST, "/endersee <player>", "Edit an online player's ender chest."),
                new CommandCard(Material.ENDER_EYE, "/vanish [player]", "Toggle vanish and item-pickup suppression."),
                new CommandCard(Material.BELL, "/sbroadcast <message>", "Broadcast a server message."),
                new CommandCard(Material.COMPASS, "/worldtp <world> [player]", "Teleport to a loaded world's spawn."),
                new CommandCard(Material.LODESTONE, "/setcustomspawn", "Store GLITG Core's custom spawn."),
                new CommandCard(Material.ENCHANTED_BOOK, "/enchant ...", "Apply policy-aware enchantments."));
        for (int index = 0; index < cards.size(); index++) {
            CommandCard card = cards.get(index);
            inventory.setItem(CONTENT_SLOTS[index], icon(card.icon, text("<white>" + card.command + "</white>"), List.of(text("<gray>" + card.description + "</gray>")), true));
        }
        inventory.setItem(36, backIcon("Back to Content"));
        player.openInventory(inventory);
    }

    private void openDynamicList(Player player, DynamicSchema schema, int page) {
        MenuHolder holder = new MenuHolder(MenuType.DYNAMIC_LIST, GuiCatalog.FEATURES.get(schema.feature).category(), schema.feature, page, schema.name);
        Inventory inventory = paged(holder, schema.label);
        ConfigurationSection root = configs.file(schema.file).getConfigurationSection(schema.root);
        List<String> ids = root == null ? List.of() : root.getKeys(false).stream().sorted().toList();
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < ids.size(); index++) {
            String id = ids.get(start + index);
            boolean enabled = configs.file(schema.file).getBoolean(schema.root + "." + id + ".enabled", true);
            inventory.setItem(CONTENT_SLOTS[index], icon(dynamicIcon(schema, id), text("<white>" + id + "</white>"),
                    List.of(status(enabled), text("<gold>› Left-click</gold> <white>Configure</white>"),
                            text("<red>› Right-click</red> <white>Remove</white>")), enabled));
        }
        finishPaged(inventory, page, ids.size(), "Create " + schema.singular.toLowerCase(Locale.ROOT));
        player.openInventory(inventory);
    }

    private void openDynamicEntry(Player player, DynamicSchema schema, String id) {
        MenuHolder holder = new MenuHolder(MenuType.DYNAMIC_ENTRY, GuiCatalog.FEATURES.get(schema.feature).category(), schema.feature, 0, schema.name);
        holder.context = id;
        Inventory inventory = create(holder, 54, menuTitle(schema.singular, id));
        holder.inventory = inventory;
        fillFrame(inventory);
        inventory.setItem(4, icon(dynamicIcon(schema, id), text("<white><bold>" + id + "</bold></white>"),
                List.of(text("<gray>Changes save immediately.</gray>")), true));
        String prefix = schema.root + "." + id + ".";
        for (int index = 0; index < schema.settings.size() && index < PAGE_SIZE; index++) {
            inventory.setItem(CONTENT_SLOTS[index], settingIcon(schema.settings.get(index).under(prefix)));
        }
        inventory.setItem(45, backIcon("Back to " + schema.label));
        inventory.setItem(53, icon(Material.TNT, text("<red>Remove " + schema.singular.toLowerCase(Locale.ROOT) + "</red>"),
                List.of(text("<gray>Requires confirmation.</gray>")), false));
        player.openInventory(inventory);
    }

    private Inventory paged(MenuHolder holder, String title) {
        Inventory inventory = create(holder, 54, menuTitle(title));
        holder.inventory = inventory;
        fillFrame(inventory);
        return inventory;
    }

    private void finishPaged(Inventory inventory, int page, int total, String addLabel) {
        if (page > 0) inventory.setItem(45, icon(Material.SPECTRAL_ARROW, text("<yellow>← Previous page</yellow>"),
                List.of(text("<gray>View the previous collection.</gray>")), false));
        else inventory.setItem(45, backIcon("Back"));
        if (addLabel != null) inventory.setItem(49, icon(Material.GOLD_INGOT, text("<gold><bold>＋ " + addLabel + "</bold></gold>"),
                List.of(text("<gray>Start a new configuration entry.</gray>"),
                        text("<gold>› Click</gold> <white>Create</white>")), true));
        else inventory.setItem(49, backIcon("Back"));
        int pageCount = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        inventory.setItem(50, icon(Material.GOLD_NUGGET,
                text("<gold>Page " + (page + 1) + " <dark_gray>/</dark_gray> " + pageCount + "</gold>"),
                List.of(text("<gray>" + total + (total == 1 ? " entry" : " entries") + " in this collection.</gray>")), false));
        if (total == 0 && inventory.getItem(22) == null) inventory.setItem(22, icon(Material.GRAY_DYE, text("<gray>Nothing configured yet</gray>"),
                List.of(text(addLabel == null
                        ? "<dark_gray>No entries are available.</dark_gray>"
                        : "<dark_gray>Use the gold action below to create the first entry.</dark_gray>")), false));
        if ((page + 1) * PAGE_SIZE < total) inventory.setItem(53, icon(Material.SPECTRAL_ARROW, text("<yellow>Next page →</yellow>"),
                List.of(text("<gray>View the next collection.</gray>")), false));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("glitgcore.admin")) return;
        try {
            switch (holder.type) {
                case CATEGORY -> handleCategory(player, event, holder);
                case FEATURE -> handleFeature(player, event, holder);
                case ITEM_RULES -> handleItemRules(player, event, holder);
                case RULE_ACTIONS -> handleRuleActions(player, event, holder);
                case ITEM_LIMITS -> handleItemLimits(player, event, holder);
                case PROTECTED_ITEMS -> handleProtectedItems(player, event, holder);
                case PROTECTED_ENTRY -> handleProtectedEntry(player, event, holder);
                case POTIONS -> handlePotions(player, event, holder);
                case ENCHANTMENTS -> handleEnchantments(player, event, holder);
                case RECIPES -> handleRecipes(player, event, holder);
                case RECIPE_EDITOR -> handleRecipeEditor(player, event, holder);
                case KITS -> handleKit(player, event, holder);
                case ADMIN_UTILITIES -> { if (event.getRawSlot() == 36) openFeature(player, GuiCatalog.FEATURES.get("admin-utilities")); }
                case DYNAMIC_LIST -> handleDynamicList(player, event, holder);
                case DYNAMIC_ENTRY -> handleDynamicEntry(player, event, holder);
                case CONFIRM -> handleConfirmation(player, event, holder);
            }
        } catch (Exception exception) {
            player.sendMessage(text("<red>That change was not saved:</red> <gray>" + exception.getMessage() + "</gray>"));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        InputPrompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(text("<yellow>Input cancelled.</yellow>"));
                prompt.reopen.accept(player);
                return;
            }
            try {
                Object parsed = prompt.parser.apply(input);
                prompt.action.accept(player, parsed);
                player.sendMessage(text("<green>Saved.</green>"));
                prompt.reopen.accept(player);
            } catch (Exception exception) {
                player.sendMessage(text("<red>Invalid value:</red> <gray>" + exception.getMessage() + "</gray>"));
                prompts.put(player.getUniqueId(), prompt);
                player.sendMessage(text("<gold>Try again, or type</gold> <white>cancel</white><gold>.</gold>"));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    private void handleCategory(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        int slot = event.getRawSlot();
        if (slot == 1 || slot == 4 || slot == 7) {
            openCategory(player, GuiCatalog.Category.values()[slot == 1 ? 0 : slot == 4 ? 1 : 2]);
            return;
        }
        if (slot == 48) { player.closeInventory(); return; }
        if (slot == 50) {
            try { configs.reload(); plugin.reloadServices(); player.sendMessage(text("<green>Configuration reloaded and validated.</green>")); }
            catch (Exception exception) { player.sendMessage(text("<red>Reload failed:</red> <gray>" + exception.getMessage() + "</gray>")); }
            openCategory(player, holder.category);
            return;
        }
        int index = indexOf(CONTENT_SLOTS, slot);
        List<GuiCatalog.Feature> features = GuiCatalog.features(holder.category);
        if (index < 0 || index >= features.size()) return;
        GuiCatalog.Feature feature = features.get(index);
        if (event.isRightClick()) {
            setFeature(feature.key(), !configs.enabled(feature.key()));
            openCategory(player, holder.category);
        } else openFeature(player, feature);
    }

    private void handleFeature(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        GuiCatalog.Feature feature = GuiCatalog.FEATURES.get(holder.feature);
        int slot = event.getRawSlot();
        if (slot == 4) {
            setFeature(feature.key(), !configs.enabled(feature.key()));
            openFeature(player, feature);
            return;
        }
        if (slot == 45 || slot == 49) { openCategory(player, feature.category()); return; }
        int index = indexOf(CONTENT_SLOTS, slot);
        if (index < 0) return;
        if (feature.special() != GuiCatalog.Special.NONE) {
            if (index == 0) { openSpecial(player, feature); return; }
            index--;
        }
        if (index >= feature.settings().size()) return;
        editSetting(player, feature.settings().get(index), event.isRightClick(), reopenFeature(feature));
    }

    private void openSpecial(Player player, GuiCatalog.Feature feature) {
        switch (feature.special()) {
            case ITEM_RULES -> openItemRules(player, 0);
            case ITEM_LIMITS -> openItemLimits(player, 0);
            case POTIONS -> openPotions(player, 0);
            case ENCHANTMENTS -> openEnchantments(player, 0);
            case PROTECTED_ITEMS -> openProtectedItems(player, 0);
            case RECIPES -> openRecipes(player, 0);
            case KITS -> openKit(player);
            case ADMIN_UTILITIES -> openAdminUtilities(player);
            case UNIQUE_ITEMS -> openDynamicList(player, dynamicSchemas.get("unique"), 0);
            case ALTARS -> openDynamicList(player, dynamicSchemas.get("altars"), 0);
            case RITUALS -> openDynamicList(player, dynamicSchemas.get("rituals"), 0);
            case NONE -> openFeature(player, feature);
        }
    }

    private void handleItemRules(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        int slot = event.getRawSlot();
        if (pagedNavigation(player, slot, holder, rules.rules().size(), page -> openItemRules(player, page), () -> openFeature(player, GuiCatalog.FEATURES.get("item-rules")))) return;
        if (slot == 49) { ItemStack held = held(player); if (held != null) openRuleActions(player, held); return; }
        int index = indexOf(CONTENT_SLOTS, slot);
        int position = holder.page * PAGE_SIZE + index;
        if (index < 0 || position >= rules.rules().size()) return;
        ItemRule rule = rules.rules().get(position);
        if (event.isRightClick()) confirm(player, "Remove item rule?", "This stops enforcing " + rule.id() + ".",
                target -> { rules.removeRule(rule.id()); openItemRules(target, holder.page); },
                target -> openItemRules(target, holder.page));
        else { rules.setRuleEnabled(rule.id(), !rule.enabled()); openItemRules(player, holder.page); }
    }

    private void handleRuleActions(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        if (event.getRawSlot() == 22) { openItemRules(player, 0); return; }
        int[] slots = {9,10,11,12,13,14,15,16,17};
        int index = indexOf(slots, event.getRawSlot());
        if (index < 0) return;
        rules.addRule((ItemStack) holder.payload, ItemAction.values()[index]);
        player.sendMessage(text("<green>Item restriction created.</green>"));
        openItemRules(player, 0);
    }

    private void handleItemLimits(Player player, InventoryClickEvent event, MenuHolder holder) {
        int slot = event.getRawSlot();
        if (pagedNavigation(player, slot, holder, rules.limits().size(), page -> openItemLimits(player, page), () -> openFeature(player, GuiCatalog.FEATURES.get("item-limits")))) return;
        if (slot == 49) {
            ItemStack held = held(player);
            if (held == null) return;
            prompt(player, "Enter the maximum amount for the held item.", GuiInputParser::nonNegativeInteger,
                    (target, value) -> rules.setLimit(held, (Integer) value), target -> openItemLimits(target, 0));
            return;
        }
        int index = indexOf(CONTENT_SLOTS, slot);
        int position = holder.page * PAGE_SIZE + index;
        if (index < 0 || position >= rules.limits().size()) return;
        RuleEngine.Limit limit = rules.limits().get(position);
        if (event.isRightClick()) confirm(player, "Remove item limit?", "Players will no longer be limited by " + limit.id() + ".",
                target -> { rules.removeLimit(limit.id()); openItemLimits(target, holder.page); },
                target -> openItemLimits(target, holder.page));
        else prompt(player, "Enter a new maximum for " + limit.id() + ".", GuiInputParser::nonNegativeInteger,
                (target, value) -> rules.setLimitMaximum(limit.id(), (Integer) value), target -> openItemLimits(target, holder.page));
    }

    private void handleProtectedItems(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        int slot = event.getRawSlot();
        if (pagedNavigation(player, slot, holder, rules.protectedItems().size(), page -> openProtectedItems(player, page), () -> openFeature(player, GuiCatalog.FEATURES.get("protected-items")))) return;
        if (slot == 49) {
            ItemStack held = held(player);
            if (held != null) openProtectedEntry(player, rules.addProtected(held));
            return;
        }
        int index = indexOf(CONTENT_SLOTS, slot);
        int position = holder.page * PAGE_SIZE + index;
        if (index < 0 || position >= rules.protectedItems().size()) return;
        RuleEngine.ProtectedDefinition entry = rules.protectedItems().get(position);
        if (event.isRightClick()) confirm(player, "Remove special-item definition?", "Protection flags for " + entry.id() + " will stop applying.",
                target -> { rules.removeProtected(entry.id()); openProtectedItems(target, holder.page); },
                target -> openProtectedItems(target, holder.page));
        else openProtectedEntry(player, entry.id());
    }

    private void handleProtectedEntry(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        RuleEngine.ProtectedDefinition entry = rules.protectedItems().stream().filter(candidate -> candidate.id().equals(holder.context)).findFirst().orElse(null);
        if (entry == null) { openProtectedItems(player, 0); return; }
        switch (event.getRawSlot()) {
            case 20 -> rules.setProtectedFlag(entry.id(), "immortal", !entry.immortal());
            case 22 -> rules.setProtectedFlag(entry.id(), "glowing", !entry.glowing());
            case 24 -> rules.setProtectedFlag(entry.id(), "stop-storage", !entry.stopStorage());
            case 36 -> { openProtectedItems(player, 0); return; }
            case 44 -> { confirm(player, "Remove special-item definition?", "This cannot be undone from the GUI.",
                    target -> { rules.removeProtected(entry.id()); openProtectedItems(target, 0); },
                    target -> openProtectedEntry(target, entry.id())); return; }
            default -> { return; }
        }
        openProtectedEntry(player, entry.id());
    }

    private void handlePotions(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        List<PotionType> types = Arrays.stream(PotionType.values()).sorted(Comparator.comparing(type -> type.getKey().asString())).toList();
        int slot = event.getRawSlot();
        if (slot == 46 || slot == 52) {
            String path = slot == 46 ? "potion-policy.ban-tier-1" : "potion-policy.ban-tier-2";
            boolean next = !configs.file("items.yml").getBoolean(path);
            write("items.yml", path, next);
            openPotions(player, holder.page);
            return;
        }
        if (pagedNavigation(player, slot, holder, types.size(), page -> openPotions(player, page), () -> openFeature(player, GuiCatalog.FEATURES.get("potion-policy")))) return;
        int index = indexOf(CONTENT_SLOTS, slot);
        int position = holder.page * PAGE_SIZE + index;
        if (index < 0 || position >= types.size()) return;
        String key = types.get(position).getKey().asString().toLowerCase(Locale.ROOT);
        List<String> banned = new ArrayList<>(configs.file("items.yml").getStringList("potion-policy.banned"));
        if (normalizedSet(banned).contains(key)) banned.removeIf(value -> value.equalsIgnoreCase(key)); else banned.add(key);
        banned.sort(String::compareTo);
        write("items.yml", "potion-policy.banned", banned);
        openPotions(player, holder.page);
    }

    private void handleEnchantments(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        List<Enchantment> values = new ArrayList<>();
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).forEach(values::add);
        values.sort(Comparator.comparing(enchantment -> enchantment.getKey().asString()));
        int slot = event.getRawSlot();
        if (pagedNavigation(player, slot, holder, values.size(), page -> openEnchantments(player, page), () -> openFeature(player, GuiCatalog.FEATURES.get("enchant-policy")))) return;
        int index = indexOf(CONTENT_SLOTS, slot);
        int position = holder.page * PAGE_SIZE + index;
        if (index < 0 || position >= values.size()) return;
        String key = values.get(position).getKey().asString().toLowerCase(Locale.ROOT);
        if (event.isRightClick()) {
            if (event.isShiftClick()) {
                write("enchants.yml", "maximum-levels." + key, null);
                openEnchantments(player, holder.page);
            } else prompt(player, "Enter the maximum allowed level for " + key + ".", GuiInputParser::nonNegativeInteger,
                    (target, value) -> write("enchants.yml", "maximum-levels." + key, value), target -> openEnchantments(target, holder.page));
        } else {
            List<String> banned = new ArrayList<>(configs.file("enchants.yml").getStringList("banned"));
            if (normalizedSet(banned).contains(key)) banned.removeIf(value -> value.equalsIgnoreCase(key)); else banned.add(key);
            banned.sort(String::compareTo);
            write("enchants.yml", "banned", banned);
            openEnchantments(player, holder.page);
        }
    }

    private void handleRecipes(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        List<RecipeDefinition> entries = crafting.configuredDefinitions().stream().sorted(Comparator.comparing(RecipeDefinition::id)).toList();
        int slot = event.getRawSlot();
        if (pagedNavigation(player, slot, holder, entries.size(), page -> openRecipes(player, page), () -> openFeature(player, GuiCatalog.FEATURES.get("custom-crafting")))) return;
        if (slot == 49) {
            prompt(player, "Enter a new recipe ID (letters, numbers, dot, underscore, or dash).", GuiInputParser::definitionId,
                    (target, value) -> openRecipeEditor(target, (String) value), target -> openRecipes(target, holder.page));
            return;
        }
        int index = indexOf(CONTENT_SLOTS, slot);
        int position = holder.page * PAGE_SIZE + index;
        if (index < 0 || position >= entries.size()) return;
        RecipeDefinition entry = entries.get(position);
        if (event.isRightClick() && event.isShiftClick()) confirm(player, "Remove recipe?", entry.id() + " will be unregistered immediately.",
                target -> { crafting.remove(entry.id()); openRecipes(target, holder.page); }, target -> openRecipes(target, holder.page));
        else if (event.isRightClick()) { crafting.setEnabled(entry.id(), !entry.enabled()); openRecipes(player, holder.page); }
        else openRecipeEditor(player, entry.id());
    }

    private void handleRecipeEditor(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        int slot = event.getRawSlot();
        if (slot >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            if (event.isShiftClick()) {
                holder.inventory.setItem(24, clicked.clone());
                player.sendMessage(text("<green>Recipe result selected.</green>"));
            } else {
                holder.selected = clicked.asOne();
                player.sendMessage(text("<gold>Ingredient selected.</gold> <gray>Click a grid slot to place it.</gray>"));
            }
            renderRecipeControls(holder);
            return;
        }
        if (indexOf(RECIPE_SLOTS, slot) >= 0) {
            holder.inventory.setItem(slot, event.isRightClick() || holder.selected == null ? null : holder.selected.clone());
            return;
        }
        if (slot == 24) {
            holder.inventory.setItem(24, event.isRightClick() || holder.selected == null ? null : holder.selected.clone());
            return;
        }
        switch (slot) {
            case 36 -> openRecipes(player, 0);
            case 39 -> { holder.recipeType = holder.recipeType == RecipeDefinition.Type.SHAPED ? RecipeDefinition.Type.SHAPELESS : RecipeDefinition.Type.SHAPED; renderRecipeControls(holder); }
            case 40 -> {
                ItemStack result = holder.inventory.getItem(24);
                if (result == null || result.getType().isAir()) throw new IllegalArgumentException("select a recipe result first");
                ItemStack[] matrix = Arrays.stream(RECIPE_SLOTS).mapToObj(holder.inventory::getItem).toArray(ItemStack[]::new);
                if (Arrays.stream(matrix).allMatch(item -> item == null || item.getType().isAir())) throw new IllegalArgumentException("add at least one ingredient");
                crafting.saveRecipe((String) holder.payload, holder.recipeType, result.clone(), matrix);
                player.sendMessage(text("<green>Recipe saved and registered.</green>"));
                openRecipes(player, 0);
            }
            case 42 -> {
                String id = (String) holder.payload;
                if (crafting.configuredDefinition(id) == null) return;
                confirm(player, "Remove recipe?", id + " will be unregistered immediately.",
                        target -> { crafting.remove(id); openRecipes(target, 0); }, target -> openRecipeEditor(target, id));
            }
            default -> { }
        }
    }

    private void handleKit(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        switch (event.getRawSlot()) {
            case 1 -> { kits.setJoinEnabled(!kits.joinEnabled()); openKit(player); }
            case 3 -> confirm(player, "Replace the configured join kit?", "Your current inventory becomes the exact saved kit.",
                    target -> { kits.save(target); openKit(target); }, this::openKit);
            case 5 -> { kits.give(player, false); player.sendMessage(text("<green>Kit added to your inventory.</green>")); openKit(player); }
            case 7 -> confirm(player, "Clear the configured kit?", "The saved kit contents will be removed.",
                    target -> { kits.clear(); openKit(target); }, this::openKit);
            case 49 -> openFeature(player, GuiCatalog.FEATURES.get("join-kit"));
            default -> { }
        }
    }

    private void handleDynamicList(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        DynamicSchema schema = dynamicSchemas.get((String) holder.payload);
        ConfigurationSection root = configs.file(schema.file).getConfigurationSection(schema.root);
        List<String> ids = root == null ? List.of() : root.getKeys(false).stream().sorted().toList();
        int slot = event.getRawSlot();
        if (pagedNavigation(player, slot, holder, ids.size(), page -> openDynamicList(player, schema, page), () -> openFeature(player, GuiCatalog.FEATURES.get(schema.feature)))) return;
        if (slot == 49) {
            prompt(player, "Enter a new " + schema.singular.toLowerCase(Locale.ROOT) + " ID.", GuiInputParser::definitionId,
                    (target, value) -> { createDynamic(schema, (String) value); openDynamicEntry(target, schema, (String) value); },
                    target -> openDynamicList(target, schema, holder.page));
            return;
        }
        int index = indexOf(CONTENT_SLOTS, slot);
        int position = holder.page * PAGE_SIZE + index;
        if (index < 0 || position >= ids.size()) return;
        String id = ids.get(position);
        if (event.isRightClick()) confirm(player, "Remove " + schema.singular.toLowerCase(Locale.ROOT) + "?", id + " will be removed from configuration.",
                target -> { removeDynamic(schema, id); openDynamicList(target, schema, holder.page); }, target -> openDynamicList(target, schema, holder.page));
        else openDynamicEntry(player, schema, id);
    }

    private void handleDynamicEntry(Player player, InventoryClickEvent event, MenuHolder holder) throws IOException {
        DynamicSchema schema = dynamicSchemas.get((String) holder.payload);
        if (event.getRawSlot() == 45) { openDynamicList(player, schema, 0); return; }
        if (event.getRawSlot() == 53) {
            confirm(player, "Remove " + schema.singular.toLowerCase(Locale.ROOT) + "?", holder.context + " will be removed from configuration.",
                    target -> { removeDynamic(schema, holder.context); openDynamicList(target, schema, 0); },
                    target -> openDynamicEntry(target, schema, holder.context));
            return;
        }
        int index = indexOf(CONTENT_SLOTS, event.getRawSlot());
        if (index < 0 || index >= schema.settings.size()) return;
        GuiCatalog.Setting setting = schema.settings.get(index).under(schema.root + "." + holder.context + ".");
        editSetting(player, setting, event.isRightClick(), target -> openDynamicEntry(target, schema, holder.context));
    }

    private void handleConfirmation(Player player, InventoryClickEvent event, MenuHolder holder) throws Exception {
        Confirmation confirmation = (Confirmation) holder.payload;
        if (event.getRawSlot() == 11) confirmation.confirm.accept(player);
        else if (event.getRawSlot() == 15) confirmation.cancel.accept(player);
    }

    private void editSetting(Player player, GuiCatalog.Setting setting, boolean reverse, Consumer<Player> reopen) throws IOException {
        Object current = configs.file(setting.file()).get(setting.path());
        switch (setting.type()) {
            case BOOLEAN -> { write(setting.file(), setting.path(), !Boolean.TRUE.equals(current)); reopen.accept(player); }
            case ENUM -> {
                List<String> options = setting.options();
                int index = options.indexOf(String.valueOf(current).toUpperCase(Locale.ROOT));
                int next = Math.floorMod(index + (reverse ? -1 : 1), options.size());
                write(setting.file(), setting.path(), options.get(next));
                reopen.accept(player);
            }
            case INTEGER -> prompt(player, "Enter a whole number for " + setting.label() + ".", GuiInputParser::nonNegativeInteger,
                    (target, value) -> write(setting.file(), setting.path(), value), reopen);
            case DOUBLE -> prompt(player, "Enter a number for " + setting.label() + ".", GuiInputParser::nonNegativeDouble,
                    (target, value) -> write(setting.file(), setting.path(), value), reopen);
            case DURATION -> prompt(player, "Enter a duration for " + setting.label() + " (for example 15s or 2m).", GuiInputParser::duration,
                    (target, value) -> write(setting.file(), setting.path(), value), reopen);
            case STRING -> prompt(player, "Enter a value for " + setting.label() + ", or type clear.", input -> validatedString(setting, input),
                    (target, value) -> write(setting.file(), setting.path(), value), reopen);
            case STRING_LIST -> prompt(player, "Enter comma-separated values for " + setting.label() + ", or type clear.", GuiInputParser::stringList,
                    (target, value) -> write(setting.file(), setting.path(), value), reopen);
        }
    }

    private void prompt(Player player, String instruction, Function<String, Object> parser, CheckedInput action, Consumer<Player> reopen) {
        prompts.put(player.getUniqueId(), new InputPrompt(parser, action, reopen));
        player.closeInventory();
        player.sendMessage(text("<gold><bold>GLITG</bold></gold> <dark_gray>•</dark_gray> <white>Input</white>"));
        player.sendMessage(text("<white>" + instruction + "</white>"));
        player.sendMessage(text("<gray>Type a value in chat, or enter</gray> <yellow>cancel</yellow><gray> to return.</gray>"));
    }

    private void confirm(Player player, String title, String detail, CheckedPlayerAction confirm, Consumer<Player> cancel) {
        Confirmation confirmation = new Confirmation(confirm, cancel);
        MenuHolder holder = new MenuHolder(MenuType.CONFIRM, GuiCatalog.Category.OTHER, null, 0, confirmation);
        Inventory inventory = create(holder, 27, menuTitle("Confirm action"));
        holder.inventory = inventory;
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(4, icon(Material.TNT, text("<red><bold>" + title + "</bold></red>"),
                List.of(text("<gray>" + detail + "</gray>"), text("<red>This action takes effect immediately.</red>")), false));
        inventory.setItem(11, icon(Material.RED_CONCRETE, text("<red><bold>Confirm action</bold></red>"),
                List.of(text("<gray>Apply the change now.</gray>")), false));
        inventory.setItem(15, icon(Material.GOLD_BLOCK, text("<gold><bold>Keep current setup</bold></gold>"),
                List.of(text("<gray>Return without changing anything.</gray>")), true));
        player.openInventory(inventory);
    }

    private boolean pagedNavigation(Player player, int slot, MenuHolder holder, int total, Consumer<Integer> openPage, Runnable back) {
        if (slot == 45) {
            if (holder.page > 0) openPage.accept(holder.page - 1); else back.run();
            return true;
        }
        if (slot == 53 && (holder.page + 1) * PAGE_SIZE < total) { openPage.accept(holder.page + 1); return true; }
        if (slot == 49 && holder.inventory.getItem(49) != null && holder.inventory.getItem(49).getType() != Material.GOLD_INGOT) {
            back.run(); return true;
        }
        return false;
    }

    private void setFeature(String key, boolean value) throws IOException {
        configs.setFeature(key, value);
        plugin.reloadServices();
    }

    private void write(String file, String path, Object value) throws IOException {
        configs.file(file).set(path, value);
        configs.save(file);
        plugin.reloadServices();
    }

    private void createDynamic(DynamicSchema schema, String id) throws IOException {
        String base = schema.root + "." + id;
        if (configs.file(schema.file).contains(base)) throw new IllegalArgumentException("that ID already exists");
        for (Map.Entry<String, Object> entry : schema.defaults.entrySet()) configs.file(schema.file).set(base + "." + entry.getKey(), entry.getValue());
        if (schema.name.equals("unique")) configs.file(schema.file).set(base + ".recipe-key", "glitgcore:" + id);
        configs.save(schema.file);
        plugin.reloadServices();
    }

    private void removeDynamic(DynamicSchema schema, String id) throws IOException {
        configs.file(schema.file).set(schema.root + "." + id, null);
        configs.save(schema.file);
        plugin.reloadServices();
    }

    private void loadRecipe(Inventory inventory, RecipeDefinition definition) {
        if (definition.type() == RecipeDefinition.Type.SHAPED) {
            for (int row = 0; row < definition.shape().size(); row++) {
                String line = definition.shape().get(row);
                for (int column = 0; column < line.length(); column++) {
                    char symbol = line.charAt(column);
                    if (symbol != ' ') inventory.setItem(RECIPE_SLOTS[row * 3 + column], decode(definition.ingredients().get(symbol), 1, Material.BARRIER));
                }
            }
        } else {
            int index = 0;
            for (String value : definition.ingredients().values()) {
                if (index >= RECIPE_SLOTS.length) break;
                inventory.setItem(RECIPE_SLOTS[index++], decode(value, 1, Material.BARRIER));
            }
        }
        inventory.setItem(24, decode(definition.result(), definition.resultAmount(), Material.BARRIER));
    }

    private void renderRecipeControls(MenuHolder holder) {
        holder.inventory.setItem(4, icon(Material.WRITABLE_BOOK, text("<gold><bold>◆ Recipe editor</bold></gold>"), List.of(
                text("<gray>Click an item, then click the recipe grid.</gray>"),
                text("<gray>Shift-click an item to set the result.</gray>"),
                text("<gray>Right-click a slot to clear it.</gray>")), true));
        holder.inventory.setItem(36, backIcon("Back to custom recipes"));
        holder.inventory.setItem(39, icon(holder.recipeType == RecipeDefinition.Type.SHAPED ? Material.CRAFTING_TABLE : Material.BUNDLE,
                text("<yellow>Recipe type  /  " + friendly(holder.recipeType.name()) + "</yellow>"),
                List.of(text("<gray>Click to switch recipe type.</gray>")), true));
        holder.inventory.setItem(40, icon(Material.GOLD_BLOCK, text("<gold><bold>Save recipe</bold></gold>"),
                List.of(text("<gray>Validate, persist, and register immediately.</gray>")), true));
        holder.inventory.setItem(41, holder.selected == null
                ? icon(Material.GRAY_DYE, text("<gray>No ingredient selected</gray>"), List.of(text("<gray>Click an item in your inventory.</gray>")), false)
                : namedClone(holder.selected, "<gold>Selected ingredient</gold>", List.of(text("<gray>Click a grid slot to place a virtual copy.</gray>"))));
        holder.inventory.setItem(42, icon(Material.TNT, text("<red>Remove recipe</red>"), List.of(text("<gray>Requires confirmation.</gray>")), false));
    }

    private Map<String, DynamicSchema> buildDynamicSchemas() {
        Map<String, DynamicSchema> schemas = new HashMap<>();
        schemas.put("unique", new DynamicSchema("unique", "unique-items", "items.yml", "unique", "Unique crafts", "Unique craft", Material.MACE,
                List.of(
                        new GuiCatalog.Setting("items.yml", "enabled", "Enabled", Material.LEVER, GuiCatalog.ValueType.BOOLEAN, "Enable this global craft limit."),
                        new GuiCatalog.Setting("items.yml", "recipe-key", "Recipe key", Material.KNOWLEDGE_BOOK, GuiCatalog.ValueType.STRING, "Namespaced or configured recipe identifier."),
                        new GuiCatalog.Setting("items.yml", "limit", "Global limit", Material.HEAVY_CORE, GuiCatalog.ValueType.INTEGER, "Maximum successful crafts across the server.")),
                linkedDefaults("enabled", true, "recipe-key", "", "limit", 1)));
        schemas.put("altars", new DynamicSchema("altars", "altars", "rituals.yml", "altars", "Altars", "Altar", Material.LODESTONE,
                List.of(
                        new GuiCatalog.Setting("rituals.yml", "enabled", "Enabled", Material.LEVER, GuiCatalog.ValueType.BOOLEAN, "Allow placement and use of this altar type."),
                        new GuiCatalog.Setting("rituals.yml", "block", "Block material", Material.LODESTONE, GuiCatalog.ValueType.STRING, "Minecraft material used for the altar."),
                        new GuiCatalog.Setting("rituals.yml", "interaction-radius", "Interaction radius", Material.COMPASS, GuiCatalog.ValueType.DOUBLE, "Activation radius in blocks.")),
                linkedDefaults("enabled", true, "block", "LODESTONE", "interaction-radius", 4.0)));
        schemas.put("rituals", new DynamicSchema("rituals", "rituals", "rituals.yml", "rituals", "Rituals", "Ritual", Material.SOUL_LANTERN,
                List.of(
                        new GuiCatalog.Setting("rituals.yml", "enabled", "Enabled", Material.LEVER, GuiCatalog.ValueType.BOOLEAN, "Enable this ritual."),
                        new GuiCatalog.Setting("rituals.yml", "altar", "Altar ID", Material.LODESTONE, GuiCatalog.ValueType.STRING, "Altar definition used by the ritual."),
                        new GuiCatalog.Setting("rituals.yml", "input-material", "Input material", Material.NETHER_STAR, GuiCatalog.ValueType.STRING, "Required Minecraft material."),
                        new GuiCatalog.Setting("rituals.yml", "input-amount", "Input amount", Material.HOPPER, GuiCatalog.ValueType.INTEGER, "Items consumed when the ritual begins."),
                        new GuiCatalog.Setting("rituals.yml", "duration-seconds", "Duration", Material.CLOCK, GuiCatalog.ValueType.INTEGER, "Ritual duration in seconds."),
                        new GuiCatalog.Setting("rituals.yml", "radius", "Particle radius", Material.COMPASS, GuiCatalog.ValueType.DOUBLE, "Visual-effect radius."),
                        new GuiCatalog.Setting("rituals.yml", "particle", "Particle", Material.BLAZE_POWDER, GuiCatalog.ValueType.STRING, "Bukkit particle name."),
                        new GuiCatalog.Setting("rituals.yml", "result-material", "Result material", Material.BEACON, GuiCatalog.ValueType.STRING, "Minecraft material produced."),
                        new GuiCatalog.Setting("rituals.yml", "result-amount", "Result amount", Material.DROPPER, GuiCatalog.ValueType.INTEGER, "Items produced on completion."),
                        new GuiCatalog.Setting("rituals.yml", "commands", "Completion commands", Material.COMMAND_BLOCK, GuiCatalog.ValueType.STRING_LIST, "Comma-separated console commands.")),
                linkedDefaults("enabled", true, "altar", "basic", "input-material", "NETHER_STAR", "input-amount", 1,
                        "duration-seconds", 10, "radius", 3.0, "particle", "SOUL_FIRE_FLAME", "result-material", "BEACON", "result-amount", 1, "commands", List.of())));
        return Map.copyOf(schemas);
    }

    private static Map<String, Object> linkedDefaults(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return Map.copyOf(result);
    }

    private Material dynamicIcon(DynamicSchema schema, String id) {
        if (schema.name.equals("altars")) return material(configs.file(schema.file).getString(schema.root + "." + id + ".block"));
        if (schema.name.equals("rituals")) return material(configs.file(schema.file).getString(schema.root + "." + id + ".result-material"));
        return schema.icon;
    }

    private Consumer<Player> reopenFeature(GuiCatalog.Feature feature) { return player -> openFeature(player, feature); }

    private static String menuTitle(String section) {
        return "<gold><bold>GLITG</bold></gold> <dark_gray>•</dark_gray> <white>" + section + "</white>";
    }

    private static String menuTitle(String section, String detail) {
        return menuTitle(section) + " <dark_gray>›</dark_gray> <gray>" + detail + "</gray>";
    }

    private Inventory create(MenuHolder holder, int size, String title) {
        return Bukkit.createInventory(holder, size, text(title));
    }

    private void fillFrame(Inventory inventory) {
        ItemStack edge = icon(Material.YELLOW_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        ItemStack blank = icon(Material.BLACK_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, blank);
        for (int offset : new int[] {0, 2, 6, 8}) inventory.setItem(offset, edge);
        int bottom = inventory.getSize() - 9;
        for (int offset : new int[] {0, 2, 6, 8}) inventory.setItem(bottom + offset, edge);
        for (int slot : CONTENT_SLOTS) if (slot < inventory.getSize()) inventory.setItem(slot, null);
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = icon(material, Component.empty(), List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack toggleCard(Material material, String label, boolean enabled, String description) {
        return icon(material, text("<white>" + label + "</white>"),
                List.of(text("<gray>" + description + "</gray>"), Component.empty(), status(enabled),
                        text("<gold>› Click</gold> <white>Toggle</white>")), enabled);
    }

    private ItemStack backIcon(String label) {
        return icon(Material.SPECTRAL_ARROW, text("<yellow>← " + label + "</yellow>"),
                List.of(text("<gray>Return without losing saved changes.</gray>")), false);
    }

    private ItemStack errorIcon(String title, String detail) {
        return icon(Material.BARRIER, text("<red>" + title + "</red>"), List.of(text("<gray>" + detail + "</gray>")), false);
    }

    private ItemStack icon(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(nonItalic(name));
        meta.lore(nonItalic(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(glint);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack namedClone(ItemStack source, String name, List<Component> lore) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        meta.itemName(nonItalic(text(name)));
        meta.lore(nonItalic(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> nonItalic(List<Component> components) {
        return components.stream().map(AdminGuiService::nonItalic).toList();
    }

    private Component text(String miniMessage) { return messages.raw(miniMessage); }
    private Component status(boolean enabled) { return status(enabled, "Enabled", "Disabled"); }
    private Component status(boolean flag, String on, String off) {
        return text(flag ? "<green>◆ " + on + "</green>" : "<red>◇ " + off + "</red>");
    }

    private Component flagLine(String label, boolean flag) {
        return text((flag ? "<green>◆ " : "<red>◇ ") + label + (flag ? "</green>" : "</red>"));
    }

    private static String displayValue(Object value) {
        if (value == null) return "not set";
        if (value instanceof List<?> list) return list.isEmpty() ? "none" : String.join(", ", list.stream().map(String::valueOf).toList());
        if (value instanceof Boolean flag) return flag ? "enabled" : "disabled";
        String rendered = String.valueOf(value);
        return rendered.isBlank() ? "none" : rendered.replace('<', '‹').replace('>', '›');
    }

    private static Material material(String key) {
        if (key == null) return Material.PAPER;
        Material found = Material.matchMaterial(key);
        return found == null || found.isAir() ? Material.PAPER : found;
    }

    private static String friendly(String value) {
        if (value == null) return "Any item";
        String key = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
        String[] words = key.toLowerCase(Locale.ROOT).split("[_-]");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String joinActions(ItemRule rule) {
        return rule.actions().stream().map(action -> friendly(action.name())).sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    private static Material specialIcon(GuiCatalog.Special special) {
        return switch (special) {
            case ITEM_RULES -> Material.BARRIER;
            case ITEM_LIMITS -> Material.HOPPER;
            case POTIONS -> Material.BREWING_STAND;
            case ENCHANTMENTS -> Material.ENCHANTING_TABLE;
            case UNIQUE_ITEMS -> Material.HEAVY_CORE;
            case PROTECTED_ITEMS -> Material.TOTEM_OF_UNDYING;
            case RECIPES -> Material.KNOWLEDGE_BOOK;
            case KITS -> Material.CHEST;
            case ALTARS -> Material.LODESTONE;
            case RITUALS -> Material.SOUL_LANTERN;
            case ADMIN_UTILITIES -> Material.COMMAND_BLOCK;
            case NONE -> Material.PAPER;
        };
    }

    private static Material actionIcon(ItemAction action) {
        return switch (action) {
            case ALL -> Material.BARRIER;
            case CRAFT -> Material.CRAFTING_TABLE;
            case INTERACT -> Material.LEVER;
            case DROPPING -> Material.DROPPER;
            case PICKUP -> Material.HOPPER;
            case INVENTORY_MOVE -> Material.CHEST_MINECART;
            case STORAGE -> Material.ENDER_CHEST;
            case TRADE -> Material.EMERALD;
            case EQUIP -> Material.ARMOR_STAND;
        };
    }

    private static String actionDescription(ItemAction action) {
        return switch (action) {
            case ALL -> "Block every supported action involving the item.";
            case CRAFT -> "Block crafting, smithing, anvils, and result retrieval.";
            case INTERACT -> "Block main-hand and off-hand use.";
            case DROPPING -> "Prevent players dropping the item.";
            case PICKUP -> "Leave the item entity on the ground.";
            case INVENTORY_MOVE -> "Block clicks, drags, swaps, and automation.";
            case STORAGE -> "Prevent placement into external storage.";
            case TRADE -> "Prevent merchant-trade paths.";
            case EQUIP -> "Prevent equipping the item.";
        };
    }

    private static Set<String> normalizedSet(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private ItemStack decode(String encodedOrMaterial, int amount, Material fallback) {
        try {
            ItemStack item;
            if (encodedOrMaterial != null && encodedOrMaterial.startsWith("base64:")) item = ItemStackCodec.decode(encodedOrMaterial.substring(7));
            else item = new ItemStack(material(encodedOrMaterial));
            if (!item.getType().isAir()) item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
            return item;
        } catch (IOException | RuntimeException exception) {
            return errorIcon("Unreadable item", exception.getMessage() == null ? "Invalid serialized item" : exception.getMessage());
        }
    }

    private ItemStack held(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(text("<yellow>Hold the exact item you want to configure first.</yellow>"));
            return null;
        }
        return held.clone();
    }

    private Object validatedString(GuiCatalog.Setting setting, String input) {
        String value = GuiInputParser.stringValue(input);
        String path = setting.path();
        if (path.endsWith("material") || path.endsWith(".block")) {
            Material material = Material.matchMaterial(value);
            if (material == null || material.isAir()) throw new IllegalArgumentException("unknown Minecraft material");
            return material.name();
        }
        if (path.endsWith(".particle")) {
            try { return org.bukkit.Particle.valueOf(value.toUpperCase(Locale.ROOT)).name(); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown Bukkit particle"); }
        }
        if (path.endsWith(".altar") && !value.isEmpty()) return GuiInputParser.definitionId(value);
        if (path.endsWith("recipe-key") && !value.isEmpty()) {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(value.contains(":") ? value : "glitgcore:" + value);
            if (key == null) throw new IllegalArgumentException("enter a valid namespaced recipe key");
            return key.asString();
        }
        return value;
    }

    private static int indexOf(int[] values, int target) {
        for (int index = 0; index < values.length; index++) if (values[index] == target) return index;
        return -1;
    }

    private enum MenuType {
        CATEGORY, FEATURE, ITEM_RULES, RULE_ACTIONS, ITEM_LIMITS, PROTECTED_ITEMS, PROTECTED_ENTRY,
        POTIONS, ENCHANTMENTS, RECIPES, RECIPE_EDITOR, KITS, ADMIN_UTILITIES, DYNAMIC_LIST,
        DYNAMIC_ENTRY, CONFIRM
    }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final GuiCatalog.Category category;
        private final String feature;
        private final int page;
        private final Object payload;
        private Inventory inventory;
        private String context;
        private RecipeDefinition.Type recipeType;
        private ItemStack selected;

        private MenuHolder(MenuType type, GuiCatalog.Category category, String feature, int page, Object payload) {
            this.type = type; this.category = category; this.feature = feature; this.page = Math.max(0, page); this.payload = payload;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private record InputPrompt(Function<String, Object> parser, CheckedInput action, Consumer<Player> reopen) {}
    private record Confirmation(CheckedPlayerAction confirm, Consumer<Player> cancel) {}
    private record CommandCard(Material icon, String command, String description) {}
    private record DynamicSchema(String name, String feature, String file, String root, String label, String singular,
                                 Material icon, List<GuiCatalog.Setting> settings, Map<String, Object> defaults) {}

    @FunctionalInterface private interface CheckedInput { void accept(Player player, Object value) throws Exception; }
    @FunctionalInterface private interface CheckedPlayerAction { void accept(Player player) throws Exception; }
}
