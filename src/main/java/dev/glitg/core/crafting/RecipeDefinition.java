package dev.glitg.core.crafting;

import java.util.List;
import java.util.Map;

public record RecipeDefinition(String id, boolean enabled, Type type, String result, int resultAmount,
                               List<String> shape, Map<Character, String> ingredients) {
    public RecipeDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("recipe id is required");
        if (result == null || result.isBlank()) throw new IllegalArgumentException("recipe result is required");
        if (resultAmount < 1 || resultAmount > 99) throw new IllegalArgumentException("invalid result amount");
        shape = List.copyOf(shape == null ? List.of() : shape);
        ingredients = Map.copyOf(ingredients == null ? Map.of() : ingredients);
        if (type == Type.SHAPED) {
            if (shape.isEmpty() || shape.size() > 3 || shape.stream().anyMatch(row -> row.length() > 3)) {
                throw new IllegalArgumentException("shaped recipes require 1-3 rows of 1-3 characters");
            }
            for (String row : shape) for (char symbol : row.toCharArray()) {
                if (symbol != ' ' && !ingredients.containsKey(symbol)) throw new IllegalArgumentException("missing ingredient " + symbol);
            }
        } else if (ingredients.isEmpty()) throw new IllegalArgumentException("shapeless recipe needs ingredients");
    }

    public enum Type { SHAPED, SHAPELESS }
}
