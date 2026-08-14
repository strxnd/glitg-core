package dev.glitg.core.persistence;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryUniqueItemStore implements UniqueItemStore {
    private final Map<String, Integer> used = new HashMap<>();

    @Override
    public synchronized Allocation allocate(String id, int limit, int quantity) {
        if (limit < 0 || quantity < 1) throw new IllegalArgumentException("invalid allocation");
        int current = used.getOrDefault(id, 0);
        if (current + quantity > limit) return new Allocation(false, current, Math.max(0, limit - current));
        int next = current + quantity;
        used.put(id, next);
        return new Allocation(true, next, limit - next);
    }

    @Override public synchronized int used(String id) { return used.getOrDefault(id, 0); }

    @Override public synchronized void set(String id, int value) {
        if (value < 0) throw new IllegalArgumentException("value cannot be negative");
        used.put(id, value);
    }
}
