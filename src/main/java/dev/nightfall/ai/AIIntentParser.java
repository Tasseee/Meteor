package dev.nightfall.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Small, deterministic first-step intent parser for safe, explicitly confirmed biome searches. */
public final class AIIntentParser {
    private static final Map<String, String> BIOMES = new LinkedHashMap<>();

    static {
        BIOMES.put("minecraft:jungle", "jungle");
        BIOMES.put("minecraft:bamboo_jungle", "bamboo jungle");
        BIOMES.put("minecraft:desert", "desert");
        BIOMES.put("minecraft:cherry_grove", "cherry grove");
        BIOMES.put("minecraft:badlands", "badlands");
        BIOMES.put("minecraft:mangrove_swamp", "mangrove swamp");
        BIOMES.put("minecraft:mushroom_fields", "mushroom fields");
        BIOMES.put("minecraft:dark_forest", "dark forest");
        BIOMES.put("minecraft:savanna", "savanna");
        BIOMES.put("minecraft:taiga", "taiga");
        BIOMES.put("minecraft:swamp", "swamp");
        BIOMES.put("minecraft:plains", "plains");
        BIOMES.put("minecraft:ocean", "ocean");
        BIOMES.put("minecraft:snowy_plains", "snowy plains");
    }

    private AIIntentParser() {}

    public static String findBiomeSearch(String prompt) {
        if (prompt == null || prompt.isBlank()) return null;
        String text = prompt.toLowerCase(Locale.ROOT);
        boolean searchIntent = text.matches("(?s).*(\\b(such|suche|suchen|find|finde|finden|locate|lokalisier|wo ist|wo finde ich|wo gibt es|wo gibt’s|zeig mir|zeige mir|bring mich zu)\\b).*");
        if (!searchIntent) return null;

        if (containsAny(text, "bambusdschungel", "bamboo jungle", "bamboo-jungle")) return "minecraft:bamboo_jungle";
        if (containsAny(text, "kirschhain", "kirschwald", "cherry grove")) return "minecraft:cherry_grove";
        if (containsAny(text, "mangrovensumpf", "mangrovensumpf-biome", "mangrove swamp")) return "minecraft:mangrove_swamp";
        if (containsAny(text, "pilzfelder", "pilzinsel", "pilzland", "mushroom fields", "mushroom field")) return "minecraft:mushroom_fields";
        if (containsAny(text, "badlands", "mesa")) return "minecraft:badlands";
        if (containsAny(text, "dschungel", "jungle")) return "minecraft:jungle";
        if (containsAny(text, "wüste", "wueste", "desert")) return "minecraft:desert";
        if (containsAny(text, "dunkler wald", "dunkelwald", "dark forest")) return "minecraft:dark_forest";
        if (containsAny(text, "savanne", "savanna")) return "minecraft:savanna";
        if (containsAny(text, "taiga")) return "minecraft:taiga";
        if (containsAny(text, "sumpf", "swamp")) return "minecraft:swamp";
        if (containsAny(text, "ebene", "plains")) return "minecraft:plains";
        if (containsAny(text, "ocean", "ozean", "meer")) return "minecraft:ocean";
        if (containsAny(text, "schneeebene", "schneeeebene", "snowy plains")) return "minecraft:snowy_plains";
        return null;
    }

    public static String biomeLabel(String id) {
        return BIOMES.getOrDefault(id, id);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
