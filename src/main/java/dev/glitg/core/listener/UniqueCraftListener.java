package dev.glitg.core.listener;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.crafting.CraftingService;
import dev.glitg.core.message.MessageService;
import dev.glitg.core.persistence.UniqueItemStore;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.Keyed;

import java.util.Map;

public final class UniqueCraftListener implements Listener {
    private final ConfigService configs;
    private final MessageService messages;
    private final UniqueItemStore store;

    public UniqueCraftListener(ConfigService configs, MessageService messages, UniqueItemStore store) {
        this.configs = configs; this.messages = messages; this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!configs.enabled("unique-items") || !(event.getWhoClicked() instanceof Player player) || !(event.getRecipe() instanceof Keyed keyed)) return;
        Unique definition = find(keyed.getKey());
        if (definition == null) return;
        if (event.isShiftClick()) {
            event.setCancelled(true);
            player.sendMessage(messages.raw("<yellow>Globally limited recipes must be crafted one at a time.</yellow>"));
            return;
        }
        UniqueItemStore.Allocation allocation = store.allocate(definition.id(), definition.limit(), 1);
        if (!allocation.allocated()) {
            event.setCancelled(true);
            player.sendMessage(messages.raw("<red>The global craft limit for this item has been reached.</red>"));
        }
    }

    private Unique find(NamespacedKey recipeKey) {
        ConfigurationSection root = configs.file("items.yml").getConfigurationSection("unique");
        if (root == null) return null;
        for (String id : root.getKeys(false)) {
            if (!root.getBoolean(id + ".enabled", true)) continue;
            String configured = root.getString(id + ".recipe-key", "glitgcore:" + root.getString(id + ".recipe-id", id));
            if (recipeKey.asString().equals(configured)) return new Unique(id, root.getInt(id + ".limit", 1));
        }
        return null;
    }

    private record Unique(String id, int limit) {}
}
