package dev.glitg.core.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure schema migration logic used before a migrated YAML document is persisted. */
public final class ConfigMigration {
    public Map<String, Object> migrate(Map<String, Object> flattened) {
        var result = new LinkedHashMap<>(flattened);
        int version = result.get("config-version") instanceof Number number ? number.intValue() : 0;
        if (version < 1) {
            Object oldCombatTime = result.remove("rules.combat_time");
            if (oldCombatTime != null) result.putIfAbsent("combat.duration-seconds", oldCombatTime);
            result.put("config-version", 1);
            version = 1;
        }
        if (version < 2) {
            result.putIfAbsent("features.operator-bypass", false);
            result.put("config-version", 2);
        }
        return Map.copyOf(result);
    }
}
