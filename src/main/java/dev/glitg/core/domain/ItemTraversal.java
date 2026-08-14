package dev.glitg.core.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class ItemTraversal {
    private final int maximumDepth;
    private final int maximumNodes;

    public ItemTraversal(int maximumDepth, int maximumNodes) {
        if (maximumDepth < 0 || maximumNodes < 1) throw new IllegalArgumentException("invalid traversal limits");
        this.maximumDepth = maximumDepth;
        this.maximumNodes = maximumNodes;
    }

    public List<ItemDescriptor> flatten(List<ItemNode> roots) {
        record Pending(ItemNode node, int depth) {}
        var result = new ArrayList<ItemDescriptor>();
        var queue = new ArrayDeque<Pending>();
        roots.forEach(root -> queue.add(new Pending(root, 0)));
        Set<ItemNode> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        while (!queue.isEmpty()) {
            Pending pending = queue.removeFirst();
            if (!visited.add(pending.node())) continue;
            if (result.size() >= maximumNodes) throw new IllegalStateException("nested item traversal exceeded node limit");
            result.add(pending.node().item());
            if (pending.depth() < maximumDepth) {
                pending.node().children().forEach(child -> queue.addLast(new Pending(child, pending.depth() + 1)));
            }
        }
        return List.copyOf(result);
    }
}
