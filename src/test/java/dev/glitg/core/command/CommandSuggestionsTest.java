package dev.glitg.core.command;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CommandSuggestionsTest {
    private static CommandSuggestions.Context context(boolean manager) {
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        enchantments.put("minecraft:sharpness", 3);
        enchantments.put("minecraft:thorns", 0);
        return new CommandSuggestions.Context(
                List.of("Alex", "Steve"), List.of("world", "world_nether"),
                List.of("grace", "cooldowns"), List.of("mace"), List.of("ender_pearl", "shield"),
                List.of("mace"), List.of("basic"), List.of("basic-deadbeef"),
                List.of("remove", "0", "1", "64"), List.of("60", "3600", "604800"), enchantments,
                true, manager, manager, manager);
    }

    @Test void completesNestedRootAndCooldownArguments() {
        var context = context(true);
        assertEquals(List.of("feature"), CommandSuggestions.suggest("glitgcore", new String[]{"f"}, context));
        assertEquals(List.of("cooldowns", "grace"), CommandSuggestions.suggest("glitgcore", new String[]{"feature", ""}, context));
        assertEquals(List.of("off", "on"), CommandSuggestions.suggest("glitgcore", new String[]{"feature", "grace", ""}, context));
        assertEquals(List.of("ender_pearl", "shield"), CommandSuggestions.suggest("cooldown", new String[]{"status", ""}, context));
        assertEquals(List.of("Alex", "Steve"), CommandSuggestions.suggest("cooldown", new String[]{"reset", ""}, context));
        assertEquals(List.of("ender_pearl"), CommandSuggestions.suggest("cooldown", new String[]{"reset", "Alex", "e"}, context));
    }

    @Test void completesLivePlayersWorldsAndSelectorsOnlyInValidPositions() {
        var context = context(true);
        assertEquals(List.of("world", "world_nether"), CommandSuggestions.suggest("worldtp", new String[]{""}, context));
        assertEquals(List.of("Alex", "Steve"), CommandSuggestions.suggest("worldtp", new String[]{"world", ""}, context));
        assertEquals(List.of("@a", "@s", "Alex", "Steve"), CommandSuggestions.suggest("enchant", new String[]{""}, context));
        assertEquals(List.of(), CommandSuggestions.suggest("reply", new String[]{""}, context));
        assertEquals(List.of(), CommandSuggestions.suggest("sbroadcast", new String[]{"hello", ""}, context));
        assertEquals(List.of(), CommandSuggestions.suggest("combat", new String[]{""}, context));
        assertEquals(List.of("@a", "Alex", "Steve"), CommandSuggestions.suggest("kit", new String[]{"load", ""}, context));
        assertEquals(List.of("Alex", "Steve"), CommandSuggestions.suggest("kit", new String[]{"resetplayer", ""}, context));
    }

    @Test void completesConfigurationBackedIdsAndPolicyAwareEnchantLevels() {
        var context = context(true);
        assertEquals(List.of("mace"), CommandSuggestions.suggest("glitgcore", new String[]{"recipe", ""}, context));
        assertEquals(List.of("mace"), CommandSuggestions.suggest("uniqueitem", new String[]{"query", ""}, context));
        assertEquals(List.of("basic"), CommandSuggestions.suggest("saltar", new String[]{"place", ""}, context));
        assertEquals(List.of("basic-deadbeef"), CommandSuggestions.suggest("saltar", new String[]{"remove", ""}, context));
        assertEquals(List.of("basic-deadbeef"), CommandSuggestions.suggest("saltar", new String[]{"info", ""}, context));
        assertEquals(List.of("1", "2", "3", "remove"), CommandSuggestions.suggest("enchant", new String[]{"Steve", "sharpness", ""}, context));
        assertEquals(List.of("remove"), CommandSuggestions.suggest("enchant", new String[]{"Steve", "minecraft:thorns", ""}, context));
    }

    @Test void hidesManagementOperationsWithoutTheirAdditionalPermission() {
        var context = context(false);
        assertEquals(List.of("status"), CommandSuggestions.suggest("cooldown", new String[]{""}, context));
        assertEquals(List.of("status"), CommandSuggestions.suggest("dimension", new String[]{""}, context));
        assertEquals(List.of("status"), CommandSuggestions.suggest("anonymousdeaths", new String[]{""}, context));
        assertFalse(CommandSuggestions.suggest("dimension", new String[]{"schedule", ""}, context).contains("end"));
    }
}
