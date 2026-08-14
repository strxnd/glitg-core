package dev.glitg.core.persistence;

public interface UniqueItemStore {
    Allocation allocate(String id, int limit, int quantity);
    int used(String id);
    void set(String id, int value);

    record Allocation(boolean allocated, int used, int remaining) {}
}
