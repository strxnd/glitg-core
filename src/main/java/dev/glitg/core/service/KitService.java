package dev.glitg.core.service;

import dev.glitg.core.config.ConfigService;
import dev.glitg.core.item.ItemStackCodec;
import dev.glitg.core.persistence.SqliteDatabase;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KitService {
    private final ConfigService configs;
    private final SqliteDatabase database;
    private final Clock clock;

    public KitService(ConfigService configs, SqliteDatabase database, Clock clock) {
        this.configs = configs;
        this.database = database;
        this.clock = clock;
    }

    public void save(Player player) throws IOException {
        List<String> encoded = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) encoded.add(item == null ? "" : ItemStackCodec.encode(item));
        configs.file("kits.yml").set("join-kit", encoded);
        configs.save("kits.yml");
    }

    public void give(Player player, boolean replace) throws IOException {
        List<String> encoded = configs.file("kits.yml").getStringList("join-kit");
        if (replace) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
        }
        for (int slot = 0; slot < encoded.size(); slot++) {
            String value = encoded.get(slot);
            if (value.isBlank()) continue;
            ItemStack item = ItemStackCodec.decode(value);
            if (replace && slot < player.getInventory().getSize()) player.getInventory().setItem(slot, item);
            else player.getInventory().addItem(item).values().forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
        }
    }

    public boolean giveOnJoin(Player player) throws IOException, SQLException {
        if (database.kitClaimed(player.getUniqueId())) return false;
        if (player.hasPlayedBefore() && !database.kitResetEligible(player.getUniqueId())) {
            database.markKitClaimed(player.getUniqueId(), clock.millis());
            return false;
        }
        give(player, true);
        database.markKitClaimed(player.getUniqueId(), clock.millis());
        return true;
    }

    public void resetEligibility(UUID player) throws SQLException {
        database.resetKitClaim(player);
    }

    public void clear() throws IOException {
        configs.file("kits.yml").set("join-kit", List.of());
        configs.save("kits.yml");
    }

    public boolean joinEnabled() { return configs.file("kits.yml").getBoolean("join-enabled", false); }

    public void setJoinEnabled(boolean enabled) throws IOException {
        configs.file("kits.yml").set("join-enabled", enabled);
        configs.save("kits.yml");
    }
}
