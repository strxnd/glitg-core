package dev.glitg.core.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared visual palette for every GLITG-owned Adventure surface.
 *
 * <p>Plugin copy may keep using MiniMessage's semantic colour names; this class maps those roles onto
 * the house palette at the rendering boundary. Custom hex colours remain untouched, so server owners
 * can still opt out for one-off configured messages.</p>
 */
public final class InterfaceTheme {
    public static final String OBSIDIAN = "#0D0C09";
    public static final String GOLD = "#D4AF37";
    public static final String CHAMPAGNE = "#E8CF82";
    public static final String IVORY = "#FFF4D6";
    public static final String SILK = "#AAA28F";
    public static final String SMOKE = "#625C50";
    public static final String EMERALD = "#72C38E";
    public static final String GARNET = "#DE6B63";

    private static final Map<String, String> LEGACY_PALETTE = palette();

    private InterfaceTheme() {}

    public static String apply(String miniMessage) {
        String themed = miniMessage;
        for (Map.Entry<String, String> entry : LEGACY_PALETTE.entrySet()) {
            themed = themed.replace("<" + entry.getKey() + ">", "<" + entry.getValue() + ">")
                    .replace("</" + entry.getKey() + ">", "</" + entry.getValue() + ">");
        }
        return themed;
    }

    private static Map<String, String> palette() {
        Map<String, String> palette = new LinkedHashMap<>();
        palette.put("gold", GOLD);
        palette.put("yellow", CHAMPAGNE);
        palette.put("white", IVORY);
        palette.put("gray", SILK);
        palette.put("grey", SILK);
        palette.put("dark_gray", SMOKE);
        palette.put("dark_grey", SMOKE);
        palette.put("green", EMERALD);
        palette.put("red", GARNET);
        return Map.copyOf(palette);
    }
}
