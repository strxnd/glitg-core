package dev.glitg.core.permission;

import dev.glitg.core.config.ConfigService;
import org.bukkit.entity.Player;

/** Keeps administrative access separate from gameplay exemptions unless the owner opts in. */
public final class BypassPolicy {
    private BypassPolicy() {}

    public static boolean bypasses(ConfigService configs, Player player, String permission) {
        return shouldBypass(player.hasPermission(permission), player.isOp(), configs.enabled("operator-bypass"));
    }

    static boolean shouldBypass(boolean explicitlyGranted, boolean operator, boolean operatorBypassEnabled) {
        // Operator status is governed exclusively by the visible global toggle. This prevents server-default
        // permission inheritance from silently defeating restrictions while the GUI says bypass is disabled.
        return operator ? operatorBypassEnabled : explicitlyGranted;
    }
}
