package dev.glitg.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemLimitScopeTest {
    @Test void parsesOnlyCurrentScopes() {
        assertThrows(IllegalArgumentException.class, () -> ItemLimitScope.parse("PLAYER"));
        assertEquals(ItemLimitScope.STORED, ItemLimitScope.parse("stored"));
        assertEquals(ItemLimitScope.COMBAT_LOADOUT, ItemLimitScope.parse("COMBAT_LOADOUT"));
    }
}
