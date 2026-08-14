package dev.glitg.core.domain;

import java.util.List;

public record ItemNode(ItemDescriptor item, List<ItemNode> children) {
    public ItemNode {
        children = List.copyOf(children == null ? List.of() : children);
    }
}
