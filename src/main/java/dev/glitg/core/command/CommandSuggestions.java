package dev.glitg.core.command;

import dev.glitg.core.domain.ItemAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CommandSuggestions {
    record Context(
            List<String> players,
            List<String> worlds,
            List<String> features,
            List<String> recipes,
            List<String> cooldowns,
            List<String> uniqueItems,
            List<String> altarDefinitions,
            List<String> altarIds,
            List<String> itemLimits,
            List<String> durations,
            Map<String, Integer> enchantmentLevels,
            boolean playerSender,
            boolean canResetCooldowns,
            boolean canManageDimensions,
            boolean canManageTimers
    ) { }

    private static final List<String> ROOT_COMMANDS =
            List.of("gui", "reload", "status", "feature", "recipe", "debug", "version");
    private static final List<String> ITEM_ACTIONS =
            Arrays.stream(ItemAction.values()).map(Enum::name).toList();
    private static final List<String> KIT_ACTIONS =
            List.of("save", "load", "clear", "resetplayer", "join", "give");
    private static final List<String> BOOLEANS = List.of("on", "off");
    private static final List<String> DIMENSIONS = List.of("nether", "end");

    private CommandSuggestions() { }

    static List<String> suggest(String command, String[] args, Context context) {
        List<String> candidates = switch (command.toLowerCase(Locale.ROOT)) {
            case "glitgcore" -> root(args, context);
            case "banitem" -> at(args, 0, ITEM_ACTIONS);
            case "itemlimit" -> at(args, 0, context.itemLimits());
            case "combat", "protection", "grace", "stopgrace", "setrespawnspawn", "setcustomspawn" -> List.of();
            case "cooldown" -> cooldown(args, context);
            case "start" -> at(args, 0, context.durations());
            case "kit" -> kit(args, context);
            case "invsee", "endersee", "vanish" -> at(args, 0, context.players());
            case "sbroadcast", "reply" -> List.of();
            case "smsg" -> at(args, 0, context.players());
            case "worldtp" -> args.length == 1
                    ? context.worlds()
                    : at(args, 1, context.players());
            case "dimension" -> dimension(args, context);
            case "anonymousdeaths" -> anonymousDeaths(args, context);
            case "uniqueitem" -> uniqueItem(args, context);
            case "deathban" -> args.length == 1
                    ? List.of("status", "clear")
                    : at(args, 1, context.players());
            case "saltar" -> altar(args, context);
            case "enchant" -> enchant(args, context);
            default -> List.of();
        };
        return filter(candidates, args.length == 0 ? "" : args[args.length - 1]);
    }

    private static List<String> root(String[] args, Context context) {
        if (args.length == 1) return ROOT_COMMANDS;
        if (equals(args, 0, "feature")) {
            if (args.length == 2) return context.features();
            return at(args, 2, BOOLEANS);
        }
        if (equals(args, 0, "recipe")) return at(args, 1, context.recipes());
        return List.of();
    }

    private static List<String> cooldown(String[] args, Context context) {
        if (args.length == 1) return context.canResetCooldowns() ? List.of("status", "reset") : List.of("status");
        if (equals(args, 0, "status")) return at(args, 1, context.cooldowns());
        if (equals(args, 0, "reset")) {
            if (!context.canResetCooldowns()) return List.of();
            if (args.length == 2) return context.players();
            return at(args, 2, context.cooldowns());
        }
        return List.of();
    }

    private static List<String> kit(String[] args, Context context) {
        if (args.length == 1) return KIT_ACTIONS;
        if (equals(args, 0, "join")) return at(args, 1, BOOLEANS);
        if (equalsAny(args, 0, "give", "load") && args.length == 2) {
            List<String> candidates = new ArrayList<>(context.players());
            candidates.add("@a");
            return candidates;
        }
        if (equals(args, 0, "resetplayer")) return at(args, 1, context.players());
        return List.of();
    }

    private static List<String> dimension(String[] args, Context context) {
        if (args.length == 1) {
            return context.canManageDimensions()
                    ? List.of("status", "lock", "unlock", "schedule")
                    : List.of("status");
        }
        if (!equalsAny(args, 0, "status", "lock", "unlock", "schedule")) return List.of();
        if (!context.canManageDimensions() && !equals(args, 0, "status")) return List.of();
        if (args.length == 2) return DIMENSIONS;
        if (equals(args, 0, "schedule")) return at(args, 2, context.durations());
        return List.of();
    }

    private static List<String> anonymousDeaths(String[] args, Context context) {
        if (args.length == 1) {
            return context.canManageTimers() ? List.of("status", "start", "stop") : List.of("status");
        }
        if (context.canManageTimers() && equals(args, 0, "start")) return at(args, 1, context.durations());
        return List.of();
    }

    private static List<String> uniqueItem(String[] args, Context context) {
        if (args.length == 1) return List.of("query", "set", "reset");
        if (equalsAny(args, 0, "query", "set", "reset") && args.length == 2) return context.uniqueItems();
        if (equals(args, 0, "set")) return at(args, 2, List.of("0", "1"));
        return List.of();
    }

    private static List<String> altar(String[] args, Context context) {
        if (args.length == 1) return List.of("place", "remove", "list", "info");
        if (equals(args, 0, "place")) return at(args, 1, context.altarDefinitions());
        if (equalsAny(args, 0, "remove", "info")) return at(args, 1, context.altarIds());
        return List.of();
    }

    private static List<String> enchant(String[] args, Context context) {
        if (args.length == 1) {
            List<String> candidates = new ArrayList<>(context.players());
            candidates.add("@a");
            if (context.playerSender()) candidates.add("@s");
            return candidates;
        }
        if (args.length == 2) return List.copyOf(context.enchantmentLevels().keySet());
        if (args.length != 3) return List.of();
        String key = args[1].contains(":") ? args[1].toLowerCase(Locale.ROOT) : "minecraft:" + args[1].toLowerCase(Locale.ROOT);
        Integer maximum = context.enchantmentLevels().get(key);
        if (maximum == null) return List.of("remove");
        List<String> levels = new ArrayList<>();
        levels.add("remove");
        for (int level = 1; level <= Math.min(maximum, 10); level++) levels.add(String.valueOf(level));
        if (maximum > 10) levels.add(String.valueOf(maximum));
        return levels;
    }

    private static List<String> at(String[] args, int index, List<String> candidates) {
        return args.length == index + 1 ? candidates : List.of();
    }

    private static boolean equals(String[] args, int index, String expected) {
        return args.length > index && args[index].equalsIgnoreCase(expected);
    }

    private static boolean equalsAny(String[] args, int index, String... expected) {
        for (String value : expected) if (equals(args, index, value)) return true;
        return false;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return new LinkedHashSet<>(candidates).stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
