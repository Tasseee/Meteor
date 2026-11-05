package dev.nightfall.ai;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nightfall.Nightfall;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Local schematic importer. No network downloads or site scraping are performed.
 *
 * Supported formats:
 * - Sponge Schematic v2 (.schem)
 * - Litematica v5-v7 (.litematic), positive-sized regions only
 *
 * NBT is parsed with strict byte/file/volume limits. Non-falling BlockItems without
 * fluids or block entities keep their complete BlockState. The builder tests vanilla placement
 * contexts for a matching state and refuses placements it cannot reproduce. Block-entity NBT,
 * entities and fluid blocks are not recreated.
 */
public final class SchematicManager {
    private static final Path DIRECTORY = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("nightfall").resolve("schematics");
    private static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_NBT_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_DIMENSION = 64;
    private static final long MAX_VOLUME = 262_144L;
    private static final int MAX_BUILDABLE_BLOCKS = 5_000;
    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ -]{0,100}\\.(?i:schem|litematic)");

    public record Imported(String fileName, String format, String sourceName,
                           int width, int height, int length, int totalBlocks,
                           int buildableBlocks, int manualOnlyBlocks,
                           List<AIBuilder.RelativeBlock> buildPlan,
                           List<AIBuilder.RelativeBlock> clearPlan,
                           Map<String, Integer> materials, List<String> examples,
                           boolean containsBlockEntities, boolean containsEntities) {
        public Imported {
            buildPlan = List.copyOf(buildPlan);
            clearPlan = List.copyOf(clearPlan);
            materials = Map.copyOf(materials);
            examples = List.copyOf(examples);
        }

        public String summary() {
            StringBuilder out = new StringBuilder("SCHEMATIC-IMPORT: ").append(fileName)
                    .append("\nFormat: ").append(format)
                    .append("\nName: ").append(sourceName.isBlank() ? fileName : sourceName)
                    .append("\nAbmessungen: ").append(width).append(" × ").append(height).append(" × ").append(length)
                    .append("\nNicht-Luft-Blöcke: ").append(totalBlocks)
                    .append("\nLuftzellen für automatische Geländefreimachung: ").append(clearPlan.size())
                    .append("\nAutomatisch platzierbar: ").append(buildableBlocks)
                    .append("\nManuell erforderlich/übersprungen: ").append(manualOnlyBlocks)
                    .append("\nBlock-Entities enthalten: ").append(containsBlockEntities ? "ja (NBT wird nicht übernommen)" : "nein")
                    .append("\nEntities enthalten: ").append(containsEntities ? "ja (werden nicht gespawnt)" : "nein");
            if (!materials.isEmpty()) {
                out.append("\nMaterialien der automatisch platzierbaren Blöcke:");
                materials.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> out.append("\n- ").append(entry.getKey()).append(" × ").append(entry.getValue()));
            }
            if (!examples.isEmpty()) {
                out.append("\nBeispiele nicht automatisch platzierbarer Blöcke:");
                examples.forEach(value -> out.append("\n- ").append(value));
            }
            out.append("\n\nSICHERHEIT: Nightfall räumt vor dem Setzen die als Luft markierten Zellen im Schematic-Volumen frei "
                    + "und entfernt ersetzte Blöcke an Bauzielen. Es übernimmt die gespeicherten BlockStates und sucht mit Minecrafts "
                    + "normaler BlockItem-Platzierlogik nach einer passenden Ansetzfläche und Blickrichtung. Ein Block zählt erst nach "
                    + "exakter Serverbestätigung als gebaut; nicht reproduzierbare Zustände brechen den Bau ab. Block-Entity-Blöcke "
                    + "wie Hopper und Truhen werden mit Standardzustand platziert, ihre Inventare und Sonderdaten aus der Schematic aber "
                    + "nicht übertragen. Flüssigkeiten, fallende Blöcke und Entities werden nicht automatisch rekonstruiert; dynamische "
                    + "Redstone-/Nachbarschafts-Zustände können vom Weltkontext abhängen.");
            return out.toString();
        }
    }

    private record ParsedState(BlockState state, boolean valid) {}
    private record RawBlock(int x, int y, int z, BlockState state) {}
    private record Region(int x, int y, int z, int width, int height, int length,
                          List<BlockState> palette, long[] packedStates, boolean hasBlockEntities,
                          boolean hasEntities) {}

    private SchematicManager() {}

    public static Path directory() { return DIRECTORY; }

    public static List<String> listFiles() {
        try {
            Files.createDirectories(DIRECTORY);
            try (var files = Files.list(DIRECTORY)) {
                return files.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                            return name.endsWith(".schem") || name.endsWith(".litematic");
                        })
                        .map(path -> path.getFileName().toString())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .limit(100)
                        .toList();
            }
        } catch (IOException failure) {
            Nightfall.LOGGER.warn("Schematic-Ordner konnte nicht gelesen werden: {}", failure.getClass().getSimpleName());
            return List.of();
        }
    }

    public static String communityCatalog() {
        try (var stream = SchematicManager.class.getResourceAsStream("/nightfall/community_schematics.json")) {
            if (stream == null) return "Community-Schematic-Index fehlt im Mod-JAR.";
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var sources = root.getAsJsonArray("sources");
            StringBuilder out = new StringBuilder("COMMUNITY-SCHEMATIC-QUELLEN");
            for (var element : sources) {
                if (!element.isJsonObject()) continue;
                var item = element.getAsJsonObject();
                out.append("\n\n- ").append(item.get("title").getAsString())
                        .append("\n  Ersteller: ").append(item.has("creator") ? item.get("creator").getAsString() : "Community")
                        .append("\n  Quelle: ").append(item.get("url").getAsString())
                        .append("\n  Format: ").append(item.get("downloadFormat").getAsString())
                        .append("\n  Version: ").append(item.get("minecraftVersion").getAsString())
                        .append("\n  Maße: ").append(item.get("pageDimensions").getAsString())
                        .append("\n  Prüfung: ").append(item.get("validation").getAsString());
                if (item.has("materials") && item.getAsJsonObject("materials").size() > 0) {
                    out.append("\n  Materialliste:");
                    for (var entry : item.getAsJsonObject("materials").entrySet())
                        out.append("\n    ").append(entry.getKey()).append(" × ").append(entry.getValue().getAsInt());
                }
            }
            out.append("\n\nDie Liste enthält nur Quellen und Listing-Metadaten, keine fremden Dateien. "
                    + "Lade eine Schematic regulär auf der Quellseite herunter, lege sie in ")
                    .append(DIRECTORY).append(" und nutze inspect_schematic.");
            return out.toString();
        } catch (Exception failure) {
            return "Community-Schematic-Index konnte nicht gelesen werden: " + failure.getClass().getSimpleName();
        }
    }

    public static String listForAI() {
        List<String> files = listFiles();
        StringBuilder out = new StringBuilder("LOKALE SCHEMATICS\nOrdner: ").append(DIRECTORY);
        if (files.isEmpty()) {
            out.append("\nNoch keine Dateien vorhanden. Lade eine .schem (Sponge v2) oder .litematic (v5-v7) "
                    + "Datei von einer Community-Seite herunter und lege sie in diesen Ordner.");
        } else {
            out.append("\nGefundene Dateien:");
            for (String file : files) out.append("\n- ").append(file);
            out.append("\nBitte zuerst inspect_schematic verwenden. Erst danach build_schematic.");
        }
        out.append("\n\nDateien werden lokal gelesen. Nightfall lädt nichts automatisch von Drittseiten herunter.");
        return out.toString();
    }

    public static Imported inspect(String requestedFile) {
        Path file = resolveFile(requestedFile);
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_FILE_BYTES)
                throw new IllegalArgumentException("Die Datei muss zwischen 1 Byte und 32 MB groß sein.");
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) throw new IllegalArgumentException("Zum Interpretieren von BlockStates musst du in einer Minecraft-Welt sein.");

            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.of(MAX_NBT_BYTES));
            String lower = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            Imported result = lower.endsWith(".schem")
                    ? readSponge(file.getFileName().toString(), root, mc)
                    : readLitematic(file.getFileName().toString(), root, mc);
            if (result.buildPlan().size() > MAX_BUILDABLE_BLOCKS)
                throw new IllegalArgumentException("Diese Datei enthält mehr als " + MAX_BUILDABLE_BLOCKS
                        + " automatisch unterstützte Blöcke. Teile die Schematic in kleinere Abschnitte.");
            return result;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            Nightfall.LOGGER.warn("Schematic konnte nicht importiert werden: {}", failure.getClass().getSimpleName());
            throw new IllegalArgumentException("Schematic konnte nicht sicher gelesen werden: "
                    + failure.getClass().getSimpleName() + ". Prüfe Format, Kompression und Minecraft-Version.");
        }
    }

    public static String inspectSummary(String fileName) {
        return inspect(fileName).summary();
    }

    public static String prepareBuild(String fileName) {
        Imported imported = inspect(fileName);
        if (imported.buildPlan().isEmpty())
            throw new IllegalArgumentException("Diese Schematic enthält keine sicher automatisch platzierbaren Standard-Blöcke.");
        String preview = AIBuilder.prepareImportedStructure(imported.sourceName().isBlank()
                        ? stripExtension(imported.fileName()) : imported.sourceName(),
                imported.buildPlan(), imported.clearPlan());
        return imported.summary() + "\n\n" + preview;
    }

    private static Imported readSponge(String fileName, NbtCompound root, MinecraftClient mc)
            throws IOException, CommandSyntaxException {
        int version = root.getInt("Version", -1);
        if (version != 2)
            throw new IllegalArgumentException("Unterstützt wird Sponge .schem Version 2. Gefunden: " + version + ".");
        int width = Short.toUnsignedInt(root.getShort("Width", (short) 0));
        int height = Short.toUnsignedInt(root.getShort("Height", (short) 0));
        int length = Short.toUnsignedInt(root.getShort("Length", (short) 0));
        validateDimensions(width, height, length);

        NbtCompound paletteTag = root.getCompoundOrEmpty("Palette");
        if (paletteTag.isEmpty() || paletteTag.getSize() > 4_096)
            throw new IllegalArgumentException("Die Sponge-Palette fehlt oder enthält zu viele Einträge.");
        Map<Integer, String> paletteNames = new LinkedHashMap<>();
        for (String key : paletteTag.getKeys()) {
            int id = paletteTag.getInt(key, -1);
            if (id < 0 || id >= 4_096 || paletteNames.put(id, key) != null)
                throw new IllegalArgumentException("Ungültiger Eintrag in der Sponge-Palette.");
        }
        if (paletteNames.isEmpty()) throw new IllegalArgumentException("Die Sponge-Palette ist leer.");

        List<ParsedState> palette = new ArrayList<>();
        int maxIndex = paletteNames.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = 0; i <= maxIndex; i++) {
            String stateString = paletteNames.get(i);
            if (stateString == null) {
                palette.add(new ParsedState(null, false));
                continue;
            }
            try {
                if (!stateString.contains(":")) stateString = "minecraft:" + stateString;
                BlockState state = BlockArgumentParser.block(
                        mc.world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK), stateString, false).blockState();
                palette.add(new ParsedState(state, true));
            } catch (CommandSyntaxException invalid) {
                palette.add(new ParsedState(null, false));
            }
        }

        byte[] data = root.getByteArray("BlockData")
                .orElseThrow(() -> new IllegalArgumentException("Sponge BlockData fehlt."));
        long volume = (long) width * height * length;
        if (data.length == 0 || data.length > volume * 5L)
            throw new IllegalArgumentException("Sponge BlockData hat eine ungültige Größe.");

        int total = 0;
        int buildable = 0;
        int unsupported = 0;
        List<AIBuilder.RelativeBlock> buildPlan = new ArrayList<>();
        List<AIBuilder.RelativeBlock> clearPlan = new ArrayList<>();
        Map<String, Integer> materials = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        int[] cursor = {0};
        for (int i = 0; i < (int) volume; i++) {
            int paletteIndex = readVarInt(data, cursor);
            if (paletteIndex < 0 || paletteIndex >= palette.size())
                throw new IllegalArgumentException("BlockData verweist auf einen nicht vorhandenen Paletteneintrag.");
            ParsedState parsed = palette.get(paletteIndex);
            if (!parsed.valid() || parsed.state() == null)
                throw new IllegalArgumentException("Die Schematic nutzt einen Block, der in dieser Minecraft-Installation unbekannt ist.");
            BlockState state = parsed.state();
            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);
            if (state.isAir()) {
                clearPlan.add(new AIBuilder.RelativeBlock(x, y, z));
                continue;
            }
            total++;
            BlockItem item = safeBuildItem(state, mc, x, y, z);
            if (item == null) {
                unsupported++;
                addExample(examples, state);
                continue;
            }
            Identifier itemId = Registries.ITEM.getId(item);
            buildPlan.add(new AIBuilder.RelativeBlock(x, y, z, itemId.toString(), state));
            materials.merge(itemId.toString(), 1, Integer::sum);
            buildable++;
        }
        if (cursor[0] != data.length)
            throw new IllegalArgumentException("Sponge BlockData enthält überzählige oder abgeschnittene Daten.");
        NbtList blockEntities = root.getListOrEmpty("BlockEntities");
        NbtList entities = root.getListOrEmpty("Entities");
        return new Imported(fileName, "Sponge .schem v2", "", width, height, length, total,
                buildable, unsupported, buildPlan, clearPlan, materials, examples,
                !blockEntities.isEmpty(), !entities.isEmpty());
    }

    private static Imported readLitematic(String fileName, NbtCompound root, MinecraftClient mc)
            throws CommandSyntaxException {
        int version = root.getInt("Version", -1);
        if (version < 5 || version > 7)
            throw new IllegalArgumentException("Unterstützt werden Litematica-Schemaversionen 5 bis 7. Gefunden: " + version + ".");
        NbtCompound metadata = root.getCompoundOrEmpty("Metadata");
        NbtCompound regions = root.getCompoundOrEmpty("Regions");
        if (regions.isEmpty() || regions.getSize() > 32)
            throw new IllegalArgumentException("Die Litematic-Datei hat keine Regionen oder mehr als 32 Regionen.");

        List<Region> parsedRegions = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean containsEntities = false;
        boolean containsBlockEntities = false;

        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompoundOrEmpty(regionName);
            NbtCompound position = region.getCompoundOrEmpty("Position");
            NbtCompound size = region.getCompoundOrEmpty("Size");
            int px = position.getInt("x", 0), py = position.getInt("y", 0), pz = position.getInt("z", 0);
            int sx = size.getInt("x", 0), sy = size.getInt("y", 0), sz = size.getInt("z", 0);
            if (sx <= 0 || sy <= 0 || sz <= 0)
                throw new IllegalArgumentException("Region „" + regionName
                        + "“ hat eine negative oder leere Größe. Speichere diese Region ohne Spiegelung erneut.");
            validateDimensions(sx, sy, sz);
            long volume = (long) sx * sy * sz;

            NbtList paletteTags = region.getListOrEmpty("BlockStatePalette");
            if (paletteTags.isEmpty() || paletteTags.size() > 4_096)
                throw new IllegalArgumentException("Region „" + regionName + "“ hat eine ungültige BlockStatePalette.");
            List<BlockState> palette = new ArrayList<>(paletteTags.size());
            for (int i = 0; i < paletteTags.size(); i++) {
                NbtCompound item = paletteTags.getCompound(i).orElse(new NbtCompound());
                String stateString = item.getString("Name", "");
                NbtCompound properties = item.getCompoundOrEmpty("Properties");
                if (!properties.isEmpty()) {
                    List<String> names = properties.getKeys().stream().sorted().toList();
                    stateString += "[" + String.join(",", names.stream()
                            .map(key -> key + "=" + properties.getString(key, "")).toList()) + "]";
                }
                if (stateString.isBlank())
                    throw new IllegalArgumentException("Leerer BlockState in Region „" + regionName + "“.");
                try {
                    palette.add(BlockArgumentParser.block(
                            mc.world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK), stateString, false).blockState());
                } catch (CommandSyntaxException invalid) {
                    throw new IllegalArgumentException("Unbekannter BlockState „" + stateString
                            + "“ in Region „" + regionName + "“.");
                }
            }
            long[] packed = region.getLongArray("BlockStates")
                    .orElseThrow(() -> new IllegalArgumentException("BlockStates fehlen in Region „" + regionName + "“."));
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long requiredLongs = (volume * bits + 63L) / 64L;
            if (packed.length < requiredLongs || packed.length > requiredLongs + 2)
                throw new IllegalArgumentException("Die gepackten BlockStates in Region „" + regionName + "“ sind unvollständig.");
            NbtList blockEntities = region.getListOrEmpty("TileEntities");
            NbtList entities = region.getListOrEmpty("Entities");
            containsBlockEntities |= !blockEntities.isEmpty();
            containsEntities |= !entities.isEmpty();
            parsedRegions.add(new Region(px, py, pz, sx, sy, sz, palette, packed,
                    !blockEntities.isEmpty(), !entities.isEmpty()));
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px + sx - 1);
            maxY = Math.max(maxY, py + sy - 1);
            maxZ = Math.max(maxZ, pz + sz - 1);
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;
        validateDimensions(width, height, length);

        List<AIBuilder.RelativeBlock> buildPlan = new ArrayList<>();
        List<AIBuilder.RelativeBlock> clearPlan = new ArrayList<>();
        Map<String, Integer> materials = new TreeMap<>();
        List<String> examples = new ArrayList<>();
        int total = 0, buildable = 0, unsupported = 0;
        for (Region region : parsedRegions) {
            long volume = (long) region.width() * region.height() * region.length();
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(region.palette().size() - 1));
            for (int i = 0; i < (int) volume; i++) {
                int paletteIndex = readPackedIndex(region.packedStates(), i, bits);
                if (paletteIndex < 0 || paletteIndex >= region.palette().size())
                    throw new IllegalArgumentException("Litematic-BlockStateIndex außerhalb der Palette.");
                BlockState state = region.palette().get(paletteIndex);
                int x = region.x() - minX + i % region.width();
                int z = region.z() - minZ + (i / region.width()) % region.length();
                int y = region.y() - minY + i / (region.width() * region.length());
                if (state.isAir()) {
                    clearPlan.add(new AIBuilder.RelativeBlock(x, y, z));
                    continue;
                }
                total++;
                BlockItem item = safeBuildItem(state, mc, x, y, z);
                if (item == null) {
                    unsupported++;
                    addExample(examples, state);
                    continue;
                }
                Identifier itemId = Registries.ITEM.getId(item);
                buildPlan.add(new AIBuilder.RelativeBlock(x, y, z, itemId.toString(), state));
                materials.merge(itemId.toString(), 1, Integer::sum);
                buildable++;
            }
        }
        String name = metadata.getString("Name", "");
        return new Imported(fileName, "Litematica .litematic v" + version, name,
                width, height, length, total, buildable, unsupported,
                buildPlan, clearPlan, materials, examples, containsBlockEntities, containsEntities);
    }

    private static BlockItem safeBuildItem(BlockState state, MinecraftClient mc, int x, int y, int z) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty())
            return null;
        if (state.getBlock() instanceof FallingBlock) return null;
        return state.getBlock().asItem() instanceof BlockItem item ? item : null;
    }

    private static int readVarInt(byte[] data, int[] cursor) {
        int value = 0;
        int shift = 0;
        for (int count = 0; count < 5; count++) {
            if (cursor[0] >= data.length) throw new IllegalArgumentException("Sponge BlockData endet mitten in einem VarInt.");
            int next = data[cursor[0]++] & 0xFF;
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) return value;
            shift += 7;
        }
        throw new IllegalArgumentException("Ungültiger VarInt in Sponge BlockData.");
    }

    private static int readPackedIndex(long[] data, int index, int bits) {
        long bitIndex = (long) index * bits;
        int longIndex = (int) (bitIndex >>> 6);
        int bitOffset = (int) (bitIndex & 63L);
        if (longIndex >= data.length) throw new IllegalArgumentException("Litematic BlockStates sind abgeschnitten.");
        long value = data[longIndex] >>> bitOffset;
        int remaining = 64 - bitOffset;
        if (remaining < bits) {
            if (longIndex + 1 >= data.length) throw new IllegalArgumentException("Litematic BlockStates sind abgeschnitten.");
            value |= data[longIndex + 1] << remaining;
        }
        return (int) (value & ((1L << bits) - 1L));
    }

    private static void validateDimensions(int width, int height, int length) {
        if (width < 1 || height < 1 || length < 1
                || width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION)
            throw new IllegalArgumentException("Maximale Schematic-Abmessungen: "
                    + MAX_DIMENSION + " × " + MAX_DIMENSION + " × " + MAX_DIMENSION + ".");
        if ((long) width * height * length > MAX_VOLUME)
            throw new IllegalArgumentException("Die Schematic überschreitet das sichere Volumenlimit.");
    }

    private static void addExample(List<String> examples, BlockState state) {
        if (examples.size() >= 8) return;
        String text = state.toString();
        if (!examples.contains(text)) examples.add(text);
    }

    private static Path resolveFile(String requestedFile) {
        if (requestedFile == null || !SAFE_FILE.matcher(requestedFile).matches())
            throw new IllegalArgumentException("Dateiname ungültig. Erlaubt sind nur lokale .schem- oder .litematic-Dateien.");
        try {
            Files.createDirectories(DIRECTORY);
            Path file = DIRECTORY.resolve(requestedFile).normalize();
            if (!file.getParent().equals(DIRECTORY.normalize()) || !Files.isRegularFile(file))
                throw new IllegalArgumentException("Schematic nicht gefunden. Lege die Datei in " + DIRECTORY + ".");
            return file;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Der lokale Schematic-Ordner konnte nicht angelegt werden.");
        }
    }

    private static void addExample(Set<String> examples, BlockState state) {
        if (examples.size() < 8) examples.add(state.toString());
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }
}
