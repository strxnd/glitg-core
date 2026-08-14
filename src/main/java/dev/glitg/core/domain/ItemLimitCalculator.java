package dev.glitg.core.domain;

import java.util.List;

public final class ItemLimitCalculator {
    private final ItemMatcher matcher;

    public ItemLimitCalculator(ItemMatcher matcher) {
        this.matcher = matcher;
    }

    public int count(ItemRule matcherRule, List<ItemDescriptor> inventory) {
        return inventory.stream().filter(item -> matcher.matches(matcherRule, item)).mapToInt(ItemDescriptor::amount).sum();
    }

    public Decision evaluate(ItemRule matcherRule, int limit, List<ItemDescriptor> inventory, ItemDescriptor incoming) {
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        int current = count(matcherRule, inventory);
        int addition = matcher.matches(matcherRule, incoming) ? incoming.amount() : 0;
        int overflow = Math.max(0, current + addition - limit);
        return new Decision(current, addition, overflow == 0, overflow);
    }

    public record Decision(int current, int incoming, boolean allowed, int overflow) {}
}
