package dev.nightfall.ai;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Writes the supported subset of an imported schematic as a new Sponge v2 file.
 * This class deliberately has no Minecraft-world or Fabric-loader dependency, so the
 * on-disk format and non-destructive file handling can be tested in isolation.
 */
final class SchematicRepairExporter {
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_VOLUME = 8_000_000L;
    private static final int MAX_DIMENSION = 512;
    private static final Pattern SAFE_SOURCE_FILE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ ()-]{0,100}\\.(?i:schem|litematic)");
    private static final Pattern SAFE_OUTPUT_FILE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ ()-]{0,100}\\.(?i:schem)");

    record Cell(int x, int y, int z, String state) {
        Cell {
            if (state == null || state.isBlank())
                throw new IllegalArgumentException("Ein Export-Block benötigt einen gültigen Blockzustand.");
        }
    }

    private SchematicRepairExporter() {
    }

    static String nextFileName(Path directory, String sourceFile) {
        if (sourceFile == null || !SAFE_SOURCE_FILE.matcher(sourceFile).matches())
            throw new IllegalArgumentException("Der Quelldateiname ist für einen Reparaturexport ungültig.");

        String base = stripExtension(sourceFile);
        Path folder = directory.toAbsolutePath().normalize();
        for (int suffix = 1; suffix <= 99; suffix++) {
            String candidate = base + "_nightfall_repaired_" + suffix + ".schem";
            if (SAFE_OUTPUT_FILE.matcher(candidate).matches() && !Files.exists(folder.resolve(candidate)))
                return candidate;
        }
        throw new IllegalArgumentException("Es existieren bereits zu viele Reparaturkopien mit diesem Dateinamen.");
    }

    static Path export(Path directory, String outputName, String sourceName,
                       int width, int height, int length,
                       List<Cell> blocks, List<Cell> water) throws IOException {
        validateDimensions(width, height, length);
        if (outputName == null || !SAFE_OUTPUT_FILE.matcher(outputName).matches())
            throw new IllegalArgumentException("Der Exportdateiname ist ungültig.");
        if (sourceName == null || !SAFE_SOURCE_FILE.matcher(sourceName).matches())
            throw new IllegalArgumentException("Der Quellname für die Export-Metadaten ist ungültig.");

        Path folder = directory.toAbsolutePath().normalize();
        Files.createDirectories(folder);
        Path output = folder.resolve(outputName).normalize();
        if (!folder.equals(output.getParent()) || Files.exists(output))
            throw new IllegalArgumentException("Die Exportdatei liegt außerhalb des Schematic-Ordners oder existiert bereits.");

        int volume = Math.toIntExact((long) width * height * length);
        Map<String, Integer> paletteIds = new LinkedHashMap<>();
        paletteIds.put("minecraft:air", 0);
        int[] blockIds = new int[volume];

        addCells(blocks, width, height, length, paletteIds, blockIds);
        addCells(water, width, height, length, paletteIds, blockIds);

        NbtCompound root = new NbtCompound();
        root.putInt("Version", 2);
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);

        NbtCompound palette = new NbtCompound();
        for (Map.Entry<String, Integer> entry : paletteIds.entrySet())
            palette.putInt(entry.getKey(), entry.getValue());
        root.put("Palette", palette);
        root.putInt("PaletteMax", paletteIds.size());

        ByteArrayOutputStream blockData = new ByteArrayOutputStream();
        for (int paletteIndex : blockIds) writeVarInt(blockData, paletteIndex);
        root.putByteArray("BlockData", blockData.toByteArray());

        NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", "Nightfall repaired copy of " + sourceName);
        root.put("Metadata", metadata);

        Path temporary = Files.createTempFile(folder, ".nightfall-repair-", ".tmp");
        try {
            NbtIo.writeCompressed(root, temporary);
            long outputSize = Files.size(temporary);
            if (outputSize <= 0 || outputSize > MAX_FILE_BYTES)
                throw new IllegalArgumentException("Die bereinigte Kopie überschreitet das sichere Dateigrößenlimit.");

            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupportedAtomicMove) {
                Files.move(temporary, output);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return output;
    }

    private static void addCells(List<Cell> cells, int width, int height, int length,
                                 Map<String, Integer> paletteIds, int[] blockIds) {
        for (Cell cell : cells) {
            int index = linearIndex(cell.x(), cell.y(), cell.z(), width, height, length);
            Integer paletteIndex = paletteIds.get(cell.state());
            if (paletteIndex == null) {
                paletteIndex = paletteIds.size();
                paletteIds.put(cell.state(), paletteIndex);
            }
            blockIds[index] = paletteIndex;
        }
    }

    private static int linearIndex(int x, int y, int z, int width, int height, int length) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length)
            throw new IllegalArgumentException("Ein importierter Block liegt außerhalb der ursprünglichen Schematic-Abmessungen.");
        return (y * length + z) * width + x;
    }

    private static void validateDimensions(int width, int height, int length) {
        if (width < 1 || height < 1 || length < 1
                || width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION)
            throw new IllegalArgumentException("Maximale Schematic-Abmessungen: "
                    + MAX_DIMENSION + " × " + MAX_DIMENSION + " × " + MAX_DIMENSION + ".");
        if ((long) width * height * length > MAX_VOLUME)
            throw new IllegalArgumentException("Die Schematic überschreitet das sichere Volumenlimit.");
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        if (value < 0) throw new IllegalArgumentException("Palette-Indizes dürfen nicht negativ sein.");
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            output.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        output.write(remaining);
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }
}
