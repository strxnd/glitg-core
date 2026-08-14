package dev.glitg.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigValidator {
    public List<String> validate(Map<String, Object> values) {
        var errors = new ArrayList<String>();
        Object version = values.get("config-version");
        if (!(version instanceof Number number) || number.intValue() < 1) {
            errors.add("config-version must be an integer >= 1");
        }
        validateNonNegative(values, "combat.duration-seconds", errors);
        validateNonNegative(values, "grace.duration-seconds", errors);
        validateNonNegative(values, "death.death-ban-seconds", errors);
        validateNonNegative(values, "protections.afk.activation-seconds", errors);
        validateNonNegative(values, "protections.new-player.duration-seconds", errors);
        return List.copyOf(errors);
    }

    private static void validateNonNegative(Map<String, Object> values, String path, List<String> errors) {
        Object value = values.get(path);
        if (value != null && (!(value instanceof Number number) || number.doubleValue() < 0)) {
            errors.add(path + " must be a non-negative number");
        }
    }
}
