package dev.glitg.core.config;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern VALUE = Pattern.compile("^(\\d+)(ms|s|m|h|d)?$", Pattern.CASE_INSENSITIVE);

    private DurationParser() {}

    public static Duration parse(Object raw) {
        if (raw instanceof Number number) return Duration.ofSeconds(number.longValue());
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        var matcher = VALUE.matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException("invalid duration: " + raw);
        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2) == null ? "s" : matcher.group(2);
        return switch (unit) {
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> throw new IllegalArgumentException("unsupported duration unit: " + unit);
        };
    }
}
