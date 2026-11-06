package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.Nightfall;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Local, editable, version-scoped blueprint knowledge database.
 *
 * Bundled entries are copied to config/nightfall/blueprints.json on first use.
 * The player can extend that JSON file without rebuilding the mod. Guide-only entries
 * are deliberately kept separate from the small set of structures the block builder supports.
 */
public final class BlueprintCatalog {
    private static final Path DATABASE = FabricLoader.getInstance().getConfigDir()
            .resolve("nightfall").resolve("blueprints.json");
    private static final int MAX_ENTRIES = 250;
    private static final int MAX_CONTENT_LENGTH = 16_000;

    private record Entry(String id, String version, String name, String category,
                         List<String> tags, String automationStatus, String validation,
                         String buildTool, String buildId, String content, String source) {
        String searchText() {
            return (id + " " + name + " " + category + " " + String.join(" ", tags)
                    + " " + content).toLowerCase(Locale.ROOT);
        }
    }
    private record Scored(Entry entry, int score) {}

    private BlueprintCatalog() {}

    public static String databasePath() { return DATABASE.toString(); }

    public static String search(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        try {
            List<Entry> entries = loadEntries();
            if (entries.isEmpty()) return "Die lokale Bauplandatenbank ist leer oder ungültig: " + DATABASE;

            boolean listAll = normalized.isBlank() || normalized.equals("all")
                    || normalized.equals("alle") || normalized.equals("übersicht")
                    || normalized.equals("uebersicht") || normalized.equals("liste");
            List<Scored> matches = new ArrayList<>();
            for (Entry entry : entries) {
                if (listAll) {
                    matches.add(new Scored(entry, 1));
                    continue;
                }
                int score = 0;
                String searchable = entry.searchText();
                for (String token : normalized.split("[^\\p{L}\\p{N}:._-]+")) {
                    if (token.length() < 2) continue;
                    if (entry.id().toLowerCase(Locale.ROOT).contains(token)
                            || entry.name().toLowerCase(Locale.ROOT).contains(token)) score += 4;
                    else if (entry.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(token))
                            || entry.category().toLowerCase(Locale.ROOT).contains(token)) score += 3;
                    else if (searchable.contains(token)) score++;
                }
                if (score > 0) matches.add(new Scored(entry, score));
            }

            matches.sort(Comparator.comparingInt(Scored::score).reversed()
                    .thenComparing(s -> s.entry().name()));
            if (matches.isEmpty()) {
                return "Kein passender Eintrag für '" + query + "'. Die Datenbank enthält "
                        + entries.size() + " Einträge. Suche zum Beispiel nach Eisenfarm, Tropfstein, "
                        + "Zuckerrohr, Schutzhaus oder Wachturm. Du kannst sie bearbeiten unter "
                        + DATABASE + ".";
            }

            StringBuilder result = new StringBuilder("NIGHTFALL BLUEPRINT-DATENBANK")
                    .append("\nDatei: ").append(DATABASE)
                    .append("\nTreffer: ").append(matches.size()).append("\n");
            int limit = listAll ? Math.min(matches.size(), 20) : Math.min(matches.size(), 4);
            for (int i = 0; i < limit; i++) {
                Entry entry = matches.get(i).entry();
                result.append("\n--- ").append(entry.name()).append(" ---")
                        .append("\nID: ").append(entry.id())
                        .append("\nVersion: ").append(entry.version())
                        .append("\nKategorie: ").append(entry.category())
                        .append("\nAutomatisierung: ").append(entry.automationStatus())
                        .append("\nValidierung: ").append(entry.validation());
                if (!entry.buildTool().isBlank() && !entry.buildId().isBlank())
                    result.append("\nBuild-Werkzeug: ").append(entry.buildTool())
                            .append(" · Vorlage: ").append(entry.buildId());
                result.append("\n").append(entry.content())
                        .append("\nQuelle: ").append(entry.source()).append("\n");
                if (result.length() > 24_000) break;
            }
            result.append("\nWICHTIG: GUIDE_ONLY bedeutet Anleitung, kein ausführbarer Bauplan. "
                    + "SUPPORTED_TEMPLATE darf nur über das angegebene Nightfall-Bauwerkzeug gestartet werden. "
                    + "Jeder Weltbau benötigt weiterhin die Vorschau und Bestätigung des Spielers.");
            return result.toString();
        } catch (Exception failure) {
            Nightfall.LOGGER.warn("Blueprint-Datenbank konnte nicht gelesen werden: {}", failure.getClass().getSimpleName());
            return "Die lokale Bauplandatenbank konnte nicht gelesen werden. Prüfe die JSON-Datei "
                    + DATABASE + " (UTF-8, schemaVersion 1). Fehler: " + failure.getClass().getSimpleName();
        }
    }

    private static List<Entry> loadEntries() throws Exception {
        Files.createDirectories(DATABASE.getParent());
        if (!Files.exists(DATABASE)) {
            try (InputStream bundled = BlueprintCatalog.class.getResourceAsStream("/nightfall/blueprints.json")) {
                if (bundled == null) throw new IllegalStateException("Gebündelte blueprints.json fehlt im Mod-JAR.");
                Files.copy(bundled, DATABASE);
            }
        }
        if (Files.size(DATABASE) > 2_000_000L)
            throw new IllegalArgumentException("Die Datenbankdatei überschreitet 2 MB.");

        JsonObject root = JsonParser.parseString(Files.readString(DATABASE)).getAsJsonObject();
        if (!root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != 1)
            throw new IllegalArgumentException("Nicht unterstützte schemaVersion.");
        JsonElement entriesElement = root.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray())
            throw new IllegalArgumentException("Das Feld 'entries' muss ein Array sein.");

        JsonArray source = entriesElement.getAsJsonArray();
        if (source.size() > MAX_ENTRIES) throw new IllegalArgumentException("Zu viele Datenbankeinträge.");
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : source) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String id = read(value, "id", "");
            String version = read(value, "version", "unspecified");
            String name = read(value, "name", "");
            String category = read(value, "category", "other");
            String automation = read(value, "automationStatus", "GUIDE_ONLY");
            String validation = read(value, "validation", "Nicht ingame validiert.");
            String buildTool = read(value, "buildTool", "");
            String buildId = read(value, "buildId", "");
            String content = read(value, "content", "");
            String provenance = read(value, "source", "Unbekannte Quelle");

            if (!id.matches("[a-z0-9._-]{1,80}") || name.isBlank() || name.length() > 120
                    || version.length() > 32 || category.length() > 48
                    || automation.length() > 32 || content.isBlank()
                    || content.length() > MAX_CONTENT_LENGTH || provenance.length() > 300) continue;
            if (!List.of("GUIDE_ONLY", "SUPPORTED_TEMPLATE").contains(automation)) automation = "GUIDE_ONLY";
            entries.add(new Entry(id, version, name, category, readTags(value.get("tags")),
                    automation, validation, buildTool, buildId, content, provenance));
        }
        return entries;
    }

    private static String read(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return fallback;
        return value.getAsString().trim();
    }

    private static List<String> readTags(JsonElement value) {
        if (value == null || !value.isJsonArray()) return List.of();
        List<String> tags = new ArrayList<>();
        JsonArray array = value.getAsJsonArray();
        for (int i = 0; i < Math.min(array.size(), 40); i++) {
            JsonElement item = array.get(i);
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                String tag = item.getAsString().trim();
                if (!tag.isBlank() && tag.length() <= 64) tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }
}
