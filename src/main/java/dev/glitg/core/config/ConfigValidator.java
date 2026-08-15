package dev.glitg.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigValidator {
    public List<String> validate(Map<String, Object> values) {
        var errors = new ArrayList<String>();
        Object version = values.get("config-version");
        if (!(version instanceof Number number) || number.intValue() != ConfigService.CURRENT_VERSION) {
            errors.add("config-version must be " + ConfigService.CURRENT_VERSION);
        }
        validateNonNegative(values, "combat.duration-seconds", errors);
        validateNonNegative(values, "grace.duration-seconds", errors);
        validateNonNegative(values, "death.death-ban-seconds", errors);
        validateNonNegative(values, "protections.afk.activation-seconds", errors);
        validateNonNegative(values, "protections.new-player.duration-seconds", errors);
        validateNonNegative(values, "protections.post-death.duration-seconds", errors);
        validateNonNegative(values, "combat.danger-logging.duration-seconds", errors);
        validateNonNegative(values, "items.audit-interval-ticks", errors);
        Object multiplier = values.get("misc.breeze-rod-drop-multiplier");
        if (multiplier != null && (!(multiplier instanceof Number number) || number.intValue() < 1)) {
            errors.add("misc.breeze-rod-drop-multiplier must be an integer >= 1");
        }
        validateInstant(values, "misc.hide-invisible-deaths-until", errors);
        return List.copyOf(errors);
    }

    private static void validateInstant(Map<String, Object> values, String path, List<String> errors) {
        Object value = values.get(path);
        if (value == null || value.toString().isBlank()) return;
        try { java.time.Instant.parse(value.toString()); }
        catch (java.time.format.DateTimeParseException exception) { errors.add(path + " must be an ISO-8601 UTC timestamp"); }
    }

    private static void validateNonNegative(Map<String, Object> values, String path, List<String> errors) {
        Object value = values.get(path);
        if (value != null && (!(value instanceof Number number) || number.doubleValue() < 0)) {
            errors.add(path + " must be a non-negative number");
        }
    }
}
