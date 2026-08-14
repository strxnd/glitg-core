package dev.glitg.core.gui;

import dev.glitg.core.config.DurationParser;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class GuiInputParser {
    private GuiInputParser() {}

    static int nonNegativeInteger(String input) {
        int value = Integer.parseInt(input);
        if (value < 0) throw new IllegalArgumentException("the number cannot be negative");
        return value;
    }

    static double nonNegativeDouble(String input) {
        double value = Double.parseDouble(input);
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("enter a finite, non-negative number");
        return value;
    }

    static String duration(String input) {
        DurationParser.parse(input);
        return input.toLowerCase(Locale.ROOT);
    }

    static String stringValue(String input) {
        return input.equalsIgnoreCase("clear") ? "" : input;
    }

    static List<String> stringList(String input) {
        if (input.equalsIgnoreCase("clear")) return List.of();
        return Arrays.stream(input.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    static String definitionId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{1,48}")) {
            throw new IllegalArgumentException("use 1-48 lowercase letters, numbers, dots, underscores, or dashes");
        }
        return normalized;
    }
}
