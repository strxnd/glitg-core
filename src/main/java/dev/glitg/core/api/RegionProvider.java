package dev.glitg.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Service-provider API for plugins that expose PvP-safe regions. */
public interface RegionProvider {
    boolean isSafe(Player player, Location location);
}
