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
        return evaluateGroup(List.of(matcherRule), limit, inventory, incoming);
    }

    public Decision evaluateGroup(List<ItemRule> matcherRules, int limit, List<ItemDescriptor> inventory, ItemDescriptor incoming) {
        if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        if (matcherRules.isEmpty()) throw new IllegalArgumentException("at least one group matcher is required");
        int current = inventory.stream().filter(item -> matcherRules.stream().anyMatch(rule -> matcher.matches(rule, item)))
                .mapToInt(ItemDescriptor::amount).sum();
        int addition = matcherRules.stream().anyMatch(rule -> matcher.matches(rule, incoming)) ? incoming.amount() : 0;
        int overflow = Math.max(0, current + addition - limit);
        return new Decision(current, addition, overflow == 0, overflow);
    }

    public record Decision(int current, int incoming, boolean allowed, int overflow) {}
}
