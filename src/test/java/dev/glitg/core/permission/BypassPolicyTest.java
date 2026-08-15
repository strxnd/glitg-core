package dev.glitg.core.permission;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BypassPolicyTest {
    @Test
    void operatorsOnlyBypassWhenTheGlobalToggleIsEnabled() {
        assertFalse(BypassPolicy.shouldBypass(false, true, false));
        assertTrue(BypassPolicy.shouldBypass(false, true, true));
        assertFalse(BypassPolicy.shouldBypass(true, true, false));
    }

    @Test
    void explicitPermissionRemainsIndependentFromOperatorStatus() {
        assertTrue(BypassPolicy.shouldBypass(true, false, false));
        assertFalse(BypassPolicy.shouldBypass(false, false, true));
    }

    @Test
    void pluginManifestDoesNotSilentlyGrantBypassesToOperators() throws IOException {
        String yaml;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"))) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var root = Pattern.compile("(?ms)^  glitgcore\\.\\*:\\R(.*?)(?=^  [^ ].*:$)").matcher(yaml);
        assertTrue(root.find());
        assertFalse(root.group(1).contains("glitgcore.bypass.*"));
        assertTrue(Pattern.compile("(?m)^  glitgcore\\.bypass\\.\\*:\\R    default: false$").matcher(yaml).find());
        for (String node : new String[] {"itemrules", "itemlimits", "potions", "enchants", "protecteditems", "cooldowns", "damagecaps", "combat", "protection", "dimensions", "misc"}) {
            assertTrue(Pattern.compile("(?m)^  glitgcore\\.bypass\\." + node + ":\\R    default: false$").matcher(yaml).find(), node);
        }
    }

    @Test
    void administratorPermissionDeclaresEveryCommandCapability() throws IOException {
        String yaml;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml"))) {
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var admin = Pattern.compile("(?ms)^  glitgcore\\.admin:\\R(.*?)(?=^  [^ ].*:$)").matcher(yaml);
        assertTrue(admin.find());
        for (String node : new String[] {"items.manage", "cooldown.reset", "grace.manage", "kit.manage",
                "admin.invsee", "admin.endersee", "admin.vanish", "admin.vanish.see", "admin.broadcast",
                "admin.enchant", "admin.worldtp", "admin.setspawn", "dimension.manage", "timers.manage",
                "unique.manage", "deathban.manage", "altar.manage"}) {
            assertTrue(admin.group(1).contains("glitgcore." + node + ": true"), node);
            assertTrue(Pattern.compile("(?m)^  glitgcore\\." + Pattern.quote(node) + ":$").matcher(yaml).find(), node);
        }
    }
}
