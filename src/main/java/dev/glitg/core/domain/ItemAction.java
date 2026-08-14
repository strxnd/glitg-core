package dev.glitg.core.domain;

public enum ItemAction {
    ALL,
    CRAFT,
    INTERACT,
    DROPPING,
    PICKUP,
    INVENTORY_MOVE,
    STORAGE,
    TRADE,
    EQUIP;

    public boolean covers(ItemAction attempted) {
        return this == ALL || this == attempted;
    }
}
