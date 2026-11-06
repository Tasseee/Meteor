package dev.nightfall.ai;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchematicRepairExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesSpongeV2WithCorrectDimensionsPaletteAndCoordinateOrder() throws Exception {
        Path original = tempDir.resolve("small.schem");
        byte[] originalBytes = new byte[] { 11, 22, 33, 44 };
        Files.write(original, originalBytes);

        Path output = SchematicRepairExporter.export(
                tempDir, "small_nightfall_repaired_1.schem", "small.schem",
                2, 2, 1,
                List.of(
                        new SchematicRepairExporter.Cell(1, 0, 0, "minecraft:stone"),
                        new SchematicRepairExporter.Cell(0, 1, 0, "minecraft:glass")
                ),
                List.of(new SchematicRepairExporter.Cell(1, 1, 0, "minecraft:water[level=0]"))
        );

        assertTrue(Files.isRegularFile(output));
        assertArrayEquals(originalBytes, Files.readAllBytes(original),
                "The source file must never be changed by an export");

        NbtCompound root = NbtIo.readCompressed(output, NbtSizeTracker.of(4L * 1024L * 1024L));
        assertEquals(2, root.getInt("Version", -1));
        assertEquals((short) 2, root.getShort("Width", (short) -1));
        assertEquals((short) 2, root.getShort("Height", (short) -1));
        assertEquals((short) 1, root.getShort("Length", (short) -1));
        assertEquals(4, root.getInt("PaletteMax", -1));

        NbtCompound palette = root.getCompoundOrEmpty("Palette");
        assertEquals(0, palette.getInt("minecraft:air", -1));
        assertEquals(1, palette.getInt("minecraft:stone", -1));
        assertEquals(2, palette.getInt("minecraft:glass", -1));
        assertEquals(3, palette.getInt("minecraft:water[level=0]", -1));

        byte[] blockData = root.getByteArray("BlockData").orElseThrow();
        assertArrayEquals(new int[] { 0, 1, 2, 3 }, decodeVarInts(blockData));
        assertEquals("Nightfall repaired copy of small.schem",
                root.getCompoundOrEmpty("Metadata").getString("Name", ""));
    }

    @Test
    void choosesNextUnusedRepairFileName() throws Exception {
        Files.createFile(tempDir.resolve("base_nightfall_repaired_1.schem"));
        Files.createFile(tempDir.resolve("base_nightfall_repaired_2.schem"));

        assertEquals("base_nightfall_repaired_3.schem",
                SchematicRepairExporter.nextFileName(tempDir, "base.schem"));
    }

    @Test
    void rejectsOutOfBoundsCellsWithoutCreatingAnOutputFile() {
        assertThrows(IllegalArgumentException.class, () -> SchematicRepairExporter.export(
                tempDir, "bad_nightfall_repaired_1.schem", "bad.schem",
                1, 1, 1,
                List.of(new SchematicRepairExporter.Cell(1, 0, 0, "minecraft:stone")),
                List.of()
        ));
        assertFalse(Files.exists(tempDir.resolve("bad_nightfall_repaired_1.schem")));
    }

    @Test
    void refusesToOverwriteTheOriginalOrWriteOutsideTheSchematicFolder() throws Exception {
        Path original = tempDir.resolve("keep.schem");
        byte[] originalBytes = new byte[] { 7, 8, 9 };
        Files.write(original, originalBytes);

        assertThrows(IllegalArgumentException.class, () -> SchematicRepairExporter.export(
                tempDir, "keep.schem", "keep.schem", 1, 1, 1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> SchematicRepairExporter.export(
                tempDir, "../outside.schem", "keep.schem", 1, 1, 1, List.of(), List.of()));
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
        assertFalse(Files.exists(tempDir.getParent().resolve("outside.schem")));
    }

    @Test
    void rejectsUnsafeDimensionsAndUnsafeSourceNames() {
        assertThrows(IllegalArgumentException.class, () -> SchematicRepairExporter.export(
                tempDir, "huge_nightfall_repaired_1.schem", "huge.schem",
                513, 1, 1, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> SchematicRepairExporter.nextFileName(tempDir, "../outside.schem"));
    }

    private static int[] decodeVarInts(byte[] data) {
        int[] values = new int[data.length];
        int valueCount = 0;
        int cursor = 0;
        while (cursor < data.length) {
            int value = 0;
            int shift = 0;
            boolean complete = false;
            for (int count = 0; count < 5 && cursor < data.length; count++) {
                int next = data[cursor++] & 0xFF;
                value |= (next & 0x7F) << shift;
                if ((next & 0x80) == 0) {
                    complete = true;
                    break;
                }
                shift += 7;
            }
            if (!complete) throw new AssertionError("Truncated VarInt in exported BlockData");
            values[valueCount++] = value;
        }
        return java.util.Arrays.copyOf(values, valueCount);
    }
}
