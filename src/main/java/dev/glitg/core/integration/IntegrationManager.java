package dev.glitg.core.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import dev.glitg.core.api.RegionProvider;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IntegrationManager {
    private final JavaPlugin plugin;
    private final boolean protocolLib;
    private final boolean packetEvents;
    private final boolean worldGuard;
    private final AtomicBoolean worldGuardWarningLogged = new AtomicBoolean();

    public IntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        protocolLib = enabled("ProtocolLib");
        packetEvents = enabled("packetevents") || enabled("PacketEvents");
        worldGuard = enabled("WorldGuard");
    }

    private static boolean enabled(String name) {
        var candidate = Bukkit.getPluginManager().getPlugin(name);
        return candidate != null && candidate.isEnabled();
    }

    public boolean packetProviderAvailable() { return protocolLib || packetEvents; }
    public boolean worldGuardAvailable() { return worldGuard; }

    /** Returns true only when WorldGuard publicly reports that PVP is denied at the location. */
    public boolean isSafeRegion(Player player, Location location) {
        for (RegionProvider provider : Bukkit.getServicesManager().getRegistrations(RegionProvider.class).stream().map(registration -> registration.getProvider()).toList()) {
            if (provider.isSafe(player, location)) return true;
        }
        if (!worldGuard) return false;
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adaptedLocation = adapter.getMethod("adapt", Location.class).invoke(null, location);
            Object sessionManager = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin")
                    .getMethod("inst").invoke(null);
            Object localPlayer = sessionManager.getClass().getMethod("wrapPlayer", Player.class).invoke(sessionManager, player);
            Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Field pvpField = flags.getField("PVP");
            Object pvpFlag = pvpField.get(null);
            Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Object flagArray = Array.newInstance(stateFlag, 1);
            Array.set(flagArray, 0, pvpFlag);
            Method testState = java.util.Arrays.stream(query.getClass().getMethods())
                    .filter(method -> method.getName().equals("testState") && method.getParameterCount() == 3)
                    .findFirst().orElseThrow();
            Object result = testState.invoke(query, adaptedLocation, localPlayer, flagArray);
            return result instanceof Boolean allowed && !allowed;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (worldGuardWarningLogged.compareAndSet(false, true)) plugin.getLogger().warning("WorldGuard safe-region query failed; checks will fail open: " + exception.getMessage());
            return false;
        }
    }
}
