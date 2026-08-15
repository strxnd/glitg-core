package dev.glitg.core.domain;

import java.util.Locale;

public enum ItemLimitScope {
    CARRIED,
    STORED,
    COMBAT_LOADOUT;

    public static ItemLimitScope parse(String value) {
        if (value == null || value.isBlank()) return CARRIED;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
